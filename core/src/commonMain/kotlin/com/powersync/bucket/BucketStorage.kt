package com.powersync.bucket

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import co.touchlab.kermit.Logger
import com.powersync.db.internal.PsInternalDatabase
import com.powersync.sync.SyncDataBatch
import com.powersync.sync.SyncLocalDatabaseResult
import co.touchlab.stately.concurrency.AtomicBoolean
import kotlinx.serialization.encodeToString
import com.powersync.db.internal.InternalTable
import com.powersync.utils.JsonUtil

internal class BucketStorage(
    private val db: PsInternalDatabase,
    private val logger: Logger
) {
    private val tableNames: MutableSet<String> = mutableSetOf()
    private var hasCompletedSync = AtomicBoolean(false)
    private var pendingBucketDeletes = AtomicBoolean(false)

    /**
     * Count up, and do a compact on startup.
     */
    private var compactCounter = COMPACT_OPERATION_INTERVAL

    companion object {
        const val MAX_OP_ID = "9223372036854775807"
        const val COMPACT_OPERATION_INTERVAL = 1_000
    }

    init {
        readTableNames()
    }

    private fun readTableNames() {
        tableNames.clear()
        // Query to get existing table names
        val names = db.getExistingTableNames("ps_data_*")

        tableNames.addAll(names)
    }

    fun getMaxOpId(): String {
        return MAX_OP_ID
    }

    suspend fun getClientId(): String {
        val id = db.getOptional("SELECT powersync_client_id() as client_id") {
            it.getString(0)!!
        }
        return id ?: throw IllegalStateException("Client ID not found")
    }

    suspend fun hasCrud(): Boolean {
        return db.queries.hasCrud().awaitAsOneOrNull() == 1L
    }

    suspend fun updateLocalTarget(checkpointCallback: suspend () -> String): Boolean {
        db.getOptional(
            "SELECT target_op FROM ${InternalTable.BUCKETS} WHERE name = '\$local' AND target_op = ?",
            parameters = listOf(MAX_OP_ID),
            mapper = { cursor -> cursor.getLong(0)!! }
        )
            ?: // Nothing to update
            return false

        val seqBefore =
            db.getOptional("SELECT seq FROM sqlite_sequence WHERE name = '${InternalTable.CRUD}'") {
                it.getLong(0)!!
            } ?: // Nothing to update
            return false

        val opId = checkpointCallback()

        logger.i { "[updateLocalTarget] Updating target to checkpoint $opId" }

        return db.readTransaction {
            if (hasCrud()) {
                logger.w { "[updateLocalTarget] ps crud is not empty" }
                return@readTransaction false
            }

            val seqAfter =
                db.getOptional("SELECT seq FROM sqlite_sequence WHERE name = '${InternalTable.CRUD}'") {
                    it.getLong(0)!!
                }
                    ?: // assert isNotEmpty
                    throw AssertionError("Sqlite Sequence should not be empty")

            if (seqAfter != seqBefore) {
                // New crud data may have been uploaded since we got the checkpoint. Abort.
                return@readTransaction false
            }

            db.execute(
                "UPDATE ${InternalTable.BUCKETS} SET target_op = ? WHERE name='\$local'",
                listOf(opId)
            )
            return@readTransaction true
        }
    }

    suspend fun saveSyncData(syncDataBatch: SyncDataBatch) {
        db.writeTransaction { tx ->
            val jsonString = JsonUtil.json.encodeToString(syncDataBatch)
            tx.execute(
                "INSERT INTO powersync_operations(op, data) VALUES(?, ?)",
                listOf("save", jsonString)
            )
        }
        this.compactCounter += syncDataBatch.buckets.sumOf { it.data.size }
    }

    suspend fun getBucketStates(): List<BucketState> {
        return db.getAll(
            "SELECT name as bucket, cast(last_op as TEXT) as op_id FROM ${InternalTable.BUCKETS} WHERE pending_delete = 0",
            mapper = { cursor ->
                BucketState(
                    bucket = cursor.getString(0)!!,
                    opId = cursor.getString(1)!!
                )
            })
    }

    suspend fun removeBuckets(bucketsToDelete: List<String>) {
        bucketsToDelete.forEach { bucketName ->
            deleteBucket(bucketName)
        }
    }


    private suspend fun deleteBucket(bucketName: String) {

        db.writeTransaction{ tx ->
            tx.execute(
                "INSERT INTO powersync_operations(op, data) VALUES(?, ?)",
                listOf("delete_bucket", bucketName)
            )
        }

        this.pendingBucketDeletes.value = true
    }

    suspend fun hasCompletedSync(): Boolean {
        if (hasCompletedSync.value) {
            return true
        }

        val completedSync = db.getOptional(
            "SELECT powersync_last_synced_at()",
            mapper = { cursor ->
                cursor.getString(0)!!
            })

        return if (completedSync != null) {
            hasCompletedSync.value = true
            true
        } else {
            false
        }
    }

    suspend fun syncLocalDatabase(targetCheckpoint: Checkpoint): SyncLocalDatabaseResult {
        val result = validateChecksums(targetCheckpoint)

        if (!result.checkpointValid) {
            logger.w { "[SyncLocalDatabase] Checksums failed for ${result.checkpointFailures}" }
            result.checkpointFailures?.forEach { bucketName ->
                deleteBucket(bucketName)
            }
            result.ready = false
            return result
        }

        val bucketNames = targetCheckpoint.checksums.map { it.bucket }

        db.writeTransaction { tx ->
            tx.execute(
                "UPDATE ps_buckets SET last_op = ? WHERE name IN (SELECT json_each.value FROM json_each(?))",
                listOf(targetCheckpoint.lastOpId, JsonUtil.json.encodeToString(bucketNames))
            )

            if (targetCheckpoint.writeCheckpoint != null) {
                tx.execute(
                    "UPDATE ps_buckets SET last_op = ? WHERE name = '\$local'",
                    listOf(targetCheckpoint.writeCheckpoint),
                )
            }
        }

        val valid = updateObjectsFromBuckets()

        if (!valid) {
            return SyncLocalDatabaseResult(
                ready = false,
                checkpointValid = true,
            )
        }

        this.forceCompact()

        return SyncLocalDatabaseResult(
            ready = true,
        )
    }

    private suspend fun validateChecksums(checkpoint: Checkpoint): SyncLocalDatabaseResult {
        val res = db.getOptional(
            "SELECT powersync_validate_checkpoint(?) as result",
            parameters = listOf(JsonUtil.json.encodeToString(checkpoint)),
            mapper = { cursor ->
                cursor.getString(0)!!
            })
            ?: //no result
            return SyncLocalDatabaseResult(
                ready = false,
                checkpointValid = false,
            )

        return JsonUtil.json.decodeFromString<SyncLocalDatabaseResult>(res)
    }

    /**
     * Atomically update the local state.
     *
     * This includes creating new tables, dropping old tables, and copying data over from the oplog.
     */
    private suspend fun updateObjectsFromBuckets(): Boolean {
        return db.writeTransaction { tx ->
            val res = tx.execute(
                "INSERT INTO powersync_operations(op, data) VALUES(?, ?)",
                listOf("sync_local", "")
            )

            return@writeTransaction res == 1L
        }
    }

    private suspend fun forceCompact() {
        // Reset counter
        this.compactCounter = COMPACT_OPERATION_INTERVAL
        this.pendingBucketDeletes.value = true

        this.autoCompact()
    }


    private suspend fun autoCompact() {
        // 1. Delete buckets
        deletePendingBuckets()

        // 2. Clear REMOVE operations, only keeping PUT ones
        clearRemoveOps()
    }

    private suspend fun deletePendingBuckets() {
        if (!this.pendingBucketDeletes.value) {
            return
        }

        db.writeTransaction { tx ->
            tx.execute(
                "DELETE FROM ps_oplog WHERE bucket IN (SELECT name FROM ps_buckets WHERE pending_delete = 1 AND last_applied_op = last_op AND last_op >= target_op)",
            )

            tx.execute(
                "DELETE FROM ps_buckets WHERE pending_delete = 1 AND last_applied_op = last_op AND last_op >= target_op",
            )
            // Executed once after start-up, and again when there are pending deletes.
            pendingBucketDeletes.value = false
        }
    }

    private suspend fun clearRemoveOps() {
        if (this.compactCounter < COMPACT_OPERATION_INTERVAL) {
            return
        }

        db.writeTransaction { tx ->
            tx.execute(
                "INSERT INTO powersync_operations(op, data) VALUES (?, ?)",
                listOf("clear_remove_ops", "")
            )
        }
        this.compactCounter = 0
    }

    @Suppress("UNUSED_PARAMETER")
    fun setTargetCheckpoint(checkpoint: Checkpoint) {
        // No-op for now
    }
}
