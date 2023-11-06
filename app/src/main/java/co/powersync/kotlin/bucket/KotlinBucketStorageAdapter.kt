package co.powersync.kotlin.bucket

import android.content.ContentValues
import android.database.Cursor
import io.requery.android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

val COMPACT_OPERATION_INTERVAL = 1_000;

@Serializable
data class ValidatedCheckpointResult(
    val valid: Boolean,
    val failed_buckets: Array<String>
)

class KotlinBucketStorageAdapter(
    private val database: SQLiteDatabase,
) : BucketStorageAdapter() {
    companion object {
        private const val MAX_OP_ID = "9223372036854775807"
    }

    private val tableNames: MutableSet<String> = mutableSetOf()

    // TODO thread safe?!
    private var _hasCompletedSync = false

    // TODO thread safe?!
    private var pendingBucketDeletes = false;

    /**
     * Count up, and do a compact on startup.
     */
    private var compactCounter = COMPACT_OPERATION_INTERVAL;
    override suspend fun init() {
        _hasCompletedSync = false

        readTableNames()
    }

    private fun readTableNames() {
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
        database.beginTransaction()

        try {
            var count = 0
            for (b in batch.buckets) {

                val values = ContentValues().apply {
                    put("op", "save")
                    // TODO what prevents us from just using batch directly? instead of wrapping each bucket into each own batch
                    put("data", Json.encodeToString(SyncDataBatch(arrayOf(b))))
                }

                val result = database.insert("powersync_operations", null, values);
                println("saveSyncData $result");

                if (result == -1L) {
                    // Error occurred according to docs
                    println("Something went wrong!")
                }

                count += b.data.size
                compactCounter += count
            }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    override suspend fun removeBuckets(buckets: Array<String>) {
        for (bucket in buckets) {
            deleteBucket(bucket)
        }
    }

    suspend fun deleteBucket(bucket: String) {
        // Delete a bucket, but allow it to be re-created.
        // To achieve this, we rename the bucket to a new temp name, and change all ops to remove.
        // By itself, this new bucket would ensure that the previous objects are deleted if they contain no more references.
        // If the old bucket is re-added, this new bucket would have no effect.
        val newName = "\$delete_${bucket}_${UUID.randomUUID()}";
        println("Deleting bucket  $bucket");

        database.beginTransaction()

        try {
            val values = ContentValues().apply {
                put("op", OpTypeEnum.REMOVE.toString())
                put("data", "NULL")
            }

            val where = "op=\"${OpTypeEnum.PUT}\" AND superseded=0 AND bucket=?"

            val args = arrayOf(bucket)

            val res = database.update("ps_oplog", values, where, args);

            // Rename the bucket to the new name
            val res2 = database.update(
                "ps_oplog",
                ContentValues().apply {
                    put("bucket", newName)
                },
                "bucket=?",
                arrayOf(bucket)
            )

            val res3 = database.delete("ps_buckets", "name = ?", arrayOf(bucket))

            val res4Cursor = database.rawQuery(
                "INSERT INTO ps_buckets(name, pending_delete, last_op) SELECT ?, 1, IFNULL(MAX(op_id), 0) FROM ps_oplog WHERE bucket = ?",
                arrayOf(newName, newName)
            )


            database.setTransactionSuccessful()
        }catch(e: Exception) {
            println("deleteBucket Failed ${e.message}")
        } finally {
            database.endTransaction();
        }
        println("done deleting bucket")
        pendingBucketDeletes = true;
    }

    override suspend fun setTargetCheckpoint(checkpoint: Checkpoint) {
        // No-op for now
    }

    override fun startSession() {
        // Do nothing, yet
    }

    override suspend fun getBucketStates(): Array<BucketState> {
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

    suspend fun validateChecksums(checkpoint: Checkpoint): SyncLocalDatabaseResult {
        val query = "SELECT powersync_validate_checkpoint(?) as result"
        val cursor = database.rawQuery(query, arrayOf(Json.encodeToString<Checkpoint>(checkpoint)))
        val resultIndex = cursor.getColumnIndex("result")

        val results = mutableListOf<ValidatedCheckpointResult>()
        while (cursor.moveToNext()) {
            val resultStr = cursor.getString(resultIndex)
            val parsed = Json.decodeFromString<ValidatedCheckpointResult>(resultStr);
            results.add(parsed)
        }

        cursor.close()

        if (results.size > 1) {
            TODO("React SDK only checks first entry, what do we do now?")
        }

        if (results.isEmpty()) {
            return SyncLocalDatabaseResult(
                checkpointValid = false,
                ready = false,
                failures = arrayOf()
            )
        }

        val firstEntry = results.first()

        if (firstEntry.valid) {
            return SyncLocalDatabaseResult(
                checkpointValid = true,
                ready = true
            )
        }

        return SyncLocalDatabaseResult(
            checkpointValid = false,
            ready = false,
            failures = firstEntry.failed_buckets
        )
    }

    override suspend fun syncLocalDatabase(checkpoint: Checkpoint): SyncLocalDatabaseResult {
        val r = validateChecksums(checkpoint);

        if (!r.checkpointValid) {
            // TODO error message here
            println("Checksums failed for ${r.failures}")
            if (r.failures?.isNotEmpty() == true) {
                for (b in r.failures) {
                    deleteBucket(b);
                }
            }

            return SyncLocalDatabaseResult(
                ready = false,
                checkpointValid = false,
                failures = r.failures
            )
        }

        val bucketNames = checkpoint.buckets.map { b -> b.bucket }

        database.beginTransaction()

        try {
            val query =
                "UPDATE ps_buckets SET last_op = ? WHERE name IN (SELECT json_each.value FROM json_each(?))";
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

        //    const valid = await this.updateObjectsFromBuckets(checkpoint);
        //    if (!valid) {
        //      this.logger.debug('Not at a consistent checkpoint - cannot update local db');
        //      return { ready: false, checkpointValid: true };
        //    }
        //
        //    await this.forceCompact();
        //
        //    return {
        //      ready: true,
        //      checkpointValid: true
        //    };

        TODO("Not yet implemented")
    }

    override suspend fun hasCrud(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getCrudBatch(limit: Int?): CrudBatch? {
        TODO("Not yet implemented")
    }

    override suspend fun hasCompletedSync(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun updateLocalTarget(cb: suspend () -> String): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun autoCompact() {
        TODO("Not yet implemented")
    }

    override suspend fun forceCompact() {
        TODO("Not yet implemented")
    }

    override fun getMaxOpId(): String {
        return MAX_OP_ID
    }
}