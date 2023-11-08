package co.powersync.kotlin.bucket

import android.content.ContentValues
import android.database.Cursor
import co.powersync.kotlin.DatabaseHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

val COMPACT_OPERATION_INTERVAL = 1_000

@Serializable
data class ValidatedCheckpointResult(
    val valid: Boolean,
    val failed_buckets: Array<String>
)

class KotlinBucketStorageAdapter(
    private val dbHelp: DatabaseHelper,
) : BucketStorageAdapter() {
    companion object {
        private const val MAX_OP_ID = "9223372036854775807"
    }

    private val tableNames: MutableSet<String> = mutableSetOf()

    // TODO thread safe?!
    private var _hasCompletedSync = false

    // TODO thread safe?!
    private var pendingBucketDeletes = false

    /**
     * Count up, and do a compact on startup.
     */
    private var compactCounter = COMPACT_OPERATION_INTERVAL
    override suspend fun init() {
        _hasCompletedSync = false

        readTableNames()
    }

    private fun readTableNames() {
        val database = dbHelp.readableDatabase
        tableNames.clear()
        // Query to get existing table names
        val query = "SELECT name FROM sqlite_master WHERE type='table' AND name GLOB 'ps_data_*'"
        val cursor: Cursor = database.rawQuery(query, null)
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIndex)
            tableNames.add(name)
        }

        cursor.close()
    }

    override suspend fun saveSyncData(batch: SyncDataBatch) {
        val database = dbHelp.writableDatabase
        database.beginTransaction()

        try {
            var count = 0
            for (b in batch.buckets) {

                val bucketJsonStr = Json.encodeToString(SyncDataBatch(arrayOf(b)))
                val values = ContentValues().apply {
                    put("op", "save")
                    // TODO what prevents us from just using batch directly? instead of wrapping each bucket into each own batch
                    put("data", bucketJsonStr)
                }

                val insertId = database.insert("powersync_operations", null, values)

                if (insertId == -1L) {
                    // Error occurred according to docs
                    println("Something went wrong!")
                }

                count += b.data.size
                compactCounter += count
            }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
            database.close()
        }

        println("Finished inserting into powersync_operations")
    }

    override suspend fun removeBuckets(buckets: Array<String>) {
        for (bucket in buckets) {
            deleteBucket(bucket)
        }
    }

    fun deleteBucket(bucket: String) {
        // Delete a bucket, but allow it to be re-created.
        // To achieve this, we rename the bucket to a new temp name, and change all ops to remove.
        // By itself, this new bucket would ensure that the previous objects are deleted if they contain no more references.
        // If the old bucket is re-added, this new bucket would have no effect.
        val newName = "\$delete_${bucket}_${UUID.randomUUID()}"
        println("Deleting bucket  $bucket")

        val database = dbHelp.writableDatabase
        database.beginTransaction()

        try {
            val values = ContentValues().apply {
                put("op", "\"${OpTypeEnum.REMOVE}\"")
                put("data", "NULL")
            }

            val where = "op=\"${OpTypeEnum.PUT}\" AND superseded=0 AND bucket=?"

            val args = arrayOf(bucket)

            database.update("ps_oplog", values, where, args)

            // Rename the bucket to the new name
            database.update(
                "ps_oplog",
                ContentValues().apply {
                    put("bucket", newName)
                },
                "bucket=?",
                arrayOf(bucket)
            )

            database.delete("ps_buckets", "name = ?", arrayOf(bucket))
            val res4Cursor = database.rawQuery(
                "INSERT INTO ps_buckets(name, pending_delete, last_op) SELECT ?, 1, IFNULL(MAX(op_id), 0) FROM ps_oplog WHERE bucket = ?",
                arrayOf(newName, newName)
            )

            res4Cursor.close()
            database.setTransactionSuccessful()
        }catch(e: Exception) {
            println("deleteBucket Failed ${e.message}")
        } finally {
            database.endTransaction()
        }
        println("done deleting bucket")
        pendingBucketDeletes = true
    }

    override suspend fun setTargetCheckpoint(checkpoint: Checkpoint) {
        // No-op for now
    }

    override fun startSession() {
        // Do nothing, yet
    }

    override suspend fun getBucketStates(): Array<BucketState> {
        val database = dbHelp.readableDatabase

        val buckets = mutableSetOf<BucketState>()
        val query =
            "SELECT name as bucket, cast(last_op as TEXT) as op_id FROM ps_buckets WHERE pending_delete = 0"
        val cursor: Cursor = database.rawQuery(query, null)
        val bucketIndex = cursor.getColumnIndex("bucket")
        val opIdIndex = cursor.getColumnIndex("op_id")
        while (cursor.moveToNext()) {
            val bucket = cursor.getString(bucketIndex)
            val opId = cursor.getString(opIdIndex)
            buckets.add(BucketState(bucket, opId))
        }

        cursor.close()

        // TODO maybe we can stick to set? or list TODO x2 read up on list vs map vs set in Kotlin world
        return buckets.toTypedArray()
    }

    fun validateChecksums(checkpoint: Checkpoint): SyncLocalDatabaseResult {
        val database = dbHelp.readableDatabase

        val query = "SELECT powersync_validate_checkpoint(?) as result"
        val dataStrArr = arrayOf(Json.encodeToString(checkpoint))

        val resJsonStr = database.stringForQuery(query, dataStrArr)

        if (resJsonStr.isEmpty()) {
            return SyncLocalDatabaseResult(
                checkpointValid = false,
                ready = false,
                failures = arrayOf()
            )
        }

        val result = Json.decodeFromString<ValidatedCheckpointResult>(resJsonStr)

        if (result.valid) {
            return SyncLocalDatabaseResult(
                checkpointValid = true,
                ready = true
            )
        }

        return SyncLocalDatabaseResult(
            checkpointValid = false,
            ready = false,
            failures = result.failed_buckets
        )
    }

    override suspend fun syncLocalDatabase(checkpoint: Checkpoint): SyncLocalDatabaseResult {
        val r = validateChecksums(checkpoint)

        if (!r.checkpointValid) {
            // TODO error message here
            println("Checksums failed for ${r.failures?.joinToString( separator =", " )}")
            if (r.failures?.isNotEmpty() == true) {
                for (b in r.failures) {
                    deleteBucket(b)
                }
            }

            return SyncLocalDatabaseResult(
                ready = false,
                checkpointValid = false,
                failures = r.failures
            )
        }

        val bucketNames: List<String>? = checkpoint.buckets?.map { b -> b.bucket }

        val database = dbHelp.writableDatabase
        database.beginTransaction()

        try {
            val query =
                "UPDATE ps_buckets SET last_op = ? WHERE name IN (SELECT json_each.value FROM json_each(?))"
            val bucketJson = Json.encodeToString(bucketNames)
            database.rawQuery(
                query,
                arrayOf(checkpoint.last_op_id, bucketJson)
            )

            if (checkpoint.write_checkpoint?.isNotEmpty() == true) {
                val query2 = "UPDATE ps_buckets SET last_op = ? WHERE name = '\$local'"
                database.rawQuery(query2, arrayOf( checkpoint.write_checkpoint))
            }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }

        val valid = updateObjectsFromBuckets()
        if (!valid) {
        println("Not at a consistent checkpoint - cannot update local db")
              return SyncLocalDatabaseResult(
                  ready = false,
                  checkpointValid = true
              )
        }

        forceCompact()

        return SyncLocalDatabaseResult(
            ready = true,
            checkpointValid = true
        )
    }

    fun updateObjectsFromBuckets(): Boolean{
        /**
         * It's best to execute this on the same thread
         * https://github.com/journeyapps/powersync-sqlite-core/blob/40554dc0e71864fe74a0cb00f1e8ca4e328ff411/crates/sqlite/sqlite/sqlite3.h#L2578
         */

        val database = dbHelp.writableDatabase
        database.beginTransaction()
        try {
            val res = database.insert("powersync_operations", null, ContentValues().apply {
                put("op", "sync_local")
                put("data", "")
            })

            database.setTransactionSuccessful()

            return res == 1L
        }
        catch (e:Exception){
            // TODO proper error message
            println("updateObjectsFromBuckets Error ${e.message}")
        }
        finally {
            database.endTransaction()
            database.close()
        }

        return false
    }

    override suspend fun hasCrud(): Boolean {
        val database = dbHelp.readableDatabase

        val query = "SELECT 1 FROM ps_crud LIMIT 1"
        val cursor: Cursor = database.rawQuery(query, null)

        val hasCrud = cursor.count > 0
        cursor.close()
        return hasCrud
    }

    override suspend fun getCrudBatch(limit: Int?): CrudBatch? {
        TODO("Not yet implemented")
    }

    override suspend fun hasCompletedSync(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun updateLocalTarget(cb: suspend () -> String): Boolean {
        val database = dbHelp.readableDatabase

        val rs1Cursor = database.rawQuery(
            "SELECT target_op FROM ps_buckets WHERE name = '\$local' AND target_op = ?",
            arrayOf( MAX_OP_ID)
        )

        if (rs1Cursor.count == 0) {
            rs1Cursor.close()
            // Nothing to update
            return false
        }
        rs1Cursor.close()

        val rsCursor = database.rawQuery(
            "SELECT seq FROM sqlite_sequence WHERE name = 'ps_crud'", arrayOf()
        )

        if (rsCursor.count == 0) {
            rsCursor.close()
            // Nothing to update
            return false
        }

        rsCursor.moveToFirst()
        val seqIndex = rsCursor.getColumnIndex("seq")
        val seqBefore = rsCursor.getInt(seqIndex)

        var opId = cb()

        TODO("Not yet implemented")
    }

    suspend fun deletePendingBuckets() {
        if (pendingBucketDeletes != false) {
            val database = dbHelp.writableDatabase

            database.beginTransaction()
            try {
                database.execSQL("DELETE FROM ps_oplog WHERE bucket IN (SELECT name FROM ps_buckets WHERE pending_delete = 1 AND last_applied_op = last_op AND last_op >= target_op)")
                database.execSQL("DELETE FROM ps_buckets WHERE pending_delete = 1 AND last_applied_op = last_op AND last_op >= target_op")

                // Executed once after start-up, and again when there are pending deletes.
                pendingBucketDeletes = false
                database.setTransactionSuccessful()
            }
            finally {
                database.endTransaction()
                database.close()
            }
        }
    }

    private fun clearRemoveOps(){
        if (compactCounter < COMPACT_OPERATION_INTERVAL) {
            return;
        }

        val database = dbHelp.writableDatabase

        database.beginTransaction()
        try {
            database.insert("powersync_operations", null,ContentValues().apply {
                put("op", "clear_remove_ops")
                put("data", "")
            } )

            compactCounter = 0
            database.setTransactionSuccessful()
        }
        finally {
            database.endTransaction()
            database.close()
        }
    }

    override suspend fun autoCompact() {
        deletePendingBuckets()
        clearRemoveOps()
    }

    override suspend fun forceCompact() {
        compactCounter = COMPACT_OPERATION_INTERVAL
        pendingBucketDeletes = true

        autoCompact()
    }

    override fun getMaxOpId(): String {
        return MAX_OP_ID
    }
}