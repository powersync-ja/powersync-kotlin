package com.powersync.bucket

import co.touchlab.kermit.Logger
import co.touchlab.stately.concurrency.AtomicBoolean
import com.powersync.db.SqlCursor
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudRow
import com.powersync.db.internal.InternalDatabase
import com.powersync.db.internal.InternalTable
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.sync.SyncDataBatch
import com.powersync.sync.SyncLocalDatabaseResult
import com.powersync.utils.JsonUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

internal class BucketStorageImpl(
    private val db: InternalDatabase,
    private val logger: Logger,
) : BucketStorage {
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

    override fun getMaxOpId(): String = MAX_OP_ID

    override suspend fun getClientId(): String {
        val id =
            db.getOptional("SELECT powersync_client_id() as client_id") {
                it.getString(0)!!
            }
        return id ?: throw IllegalStateException("Client ID not found")
    }

    override suspend fun nextCrudItem(): CrudEntry? = db.getOptional(sql = nextCrudQuery, mapper = nextCrudMapper)

    override fun nextCrudItem(transaction: PowerSyncTransaction): CrudEntry? =
        transaction.getOptional(sql = nextCrudQuery, mapper = nextCrudMapper)

    private val nextCrudQuery = "SELECT id, tx_id, data FROM ${InternalTable.CRUD} ORDER BY id ASC LIMIT 1"
    private val nextCrudMapper: (SqlCursor) -> CrudEntry = { cursor ->
        CrudEntry.fromRow(
            CrudRow(
                id = cursor.getString(0)!!,
                txId = cursor.getString(1)?.toInt(),
                data = cursor.getString(2)!!,
            ),
        )
    }

    override suspend fun hasCrud(): Boolean {
        val res = db.getOptional(sql = hasCrudQuery, mapper = hasCrudMapper)
        return res == 1L
    }

    override fun hasCrud(transaction: PowerSyncTransaction): Boolean {
        val res = transaction.getOptional(sql = hasCrudQuery, mapper = hasCrudMapper)
        return res == 1L
    }

    private val hasCrudQuery = "SELECT 1 FROM ps_crud LIMIT 1"
    private val hasCrudMapper: (SqlCursor) -> Long = {
        it.getLong(0)!!
    }

    override suspend fun updateLocalTarget(checkpointCallback: suspend () -> String): Boolean {
        db.getOptional(
            "SELECT target_op FROM ${InternalTable.BUCKETS} WHERE name = '\$local' AND target_op = ?",
            parameters = listOf(MAX_OP_ID),
            mapper = { cursor -> cursor.getLong(0)!! },
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

        return db.writeTransaction { tx ->
            if (hasCrud(tx)) {
                logger.w { "[updateLocalTarget] ps crud is not empty" }
                return@writeTransaction false
            }

            val seqAfter =
                tx.getOptional("SELECT seq FROM sqlite_sequence WHERE name = '${InternalTable.CRUD}'") {
                    it.getLong(0)!!
                }
                    ?: // assert isNotEmpty
                    throw AssertionError("Sqlite Sequence should not be empty")

            if (seqAfter != seqBefore) {
                logger.d("seqAfter != seqBefore seqAfter: $seqAfter seqBefore: $seqBefore")
                // New crud data may have been uploaded since we got the checkpoint. Abort.
                return@writeTransaction false
            }

            tx.execute(
                "UPDATE ${InternalTable.BUCKETS} SET target_op = CAST(? as INTEGER) WHERE name='\$local'",
                listOf(opId),
            )

            return@writeTransaction true
        }
    }

    override suspend fun saveSyncData(syncDataBatch: SyncDataBatch) {
        db.writeTransaction { tx ->
            val jsonString = JsonUtil.json.encodeToString(syncDataBatch)
            tx.execute(
                "INSERT INTO powersync_operations(op, data) VALUES(?, ?)",
                listOf("save", jsonString),
            )
        }
        this.compactCounter += syncDataBatch.buckets.sumOf { it.data.size }
    }

    override suspend fun getBucketStates(): List<BucketState> =
        db.getAll(
            "SELECT name AS bucket, CAST(last_op AS TEXT) AS op_id FROM ${InternalTable.BUCKETS} WHERE pending_delete = 0 AND name != '\$local'",
            mapper = { cursor ->
                BucketState(
                    bucket = cursor.getString(0)!!,
                    opId = cursor.getString(1)!!,
                )
            },
        )

    override suspend fun getBucketOperationProgress(): Map<String, LocalOperationCounters> = buildMap {
        val rows = db.getAll("SELECT name, count_at_last, count_since_last FROM ps_buckets") { cursor ->
            cursor.getString(0)!! to LocalOperationCounters(
                atLast = cursor.getLong(1)!!.toInt(),
                sinceLast = cursor.getLong(2)!!.toInt(),
            )
        }

        for ((name, counters) in rows) {
            put(name, counters)
        }
    }

    override suspend fun removeBuckets(bucketsToDelete: List<String>) {
        bucketsToDelete.forEach { bucketName ->
            deleteBucket(bucketName)
        }
    }

    private suspend fun deleteBucket(bucketName: String) {
        db.writeTransaction { tx ->
            tx.execute(
                "INSERT INTO powersync_operations(op, data) VALUES(?, ?)",
                listOf("delete_bucket", bucketName),
            )
        }

        Logger.d("[deleteBucket] Done deleting")

        this.pendingBucketDeletes.value = true
    }

    override suspend fun hasCompletedSync(): Boolean {
        if (hasCompletedSync.value) {
            return true
        }

        val completedSync =
            db.getOptional(
                "SELECT powersync_last_synced_at()",
                mapper = { cursor ->
                    cursor.getString(0)!!
                },
            )

        return if (completedSync != null) {
            hasCompletedSync.value = true
            true
        } else {
            false
        }
    }

    override suspend fun syncLocalDatabase(
        targetCheckpoint: Checkpoint,
        partialPriority: BucketPriority?,
    ): SyncLocalDatabaseResult {
        val result = validateChecksums(targetCheckpoint, partialPriority)

        if (!result.checkpointValid) {
            logger.w { "[SyncLocalDatabase] Checksums failed for ${result.checkpointFailures}" }
            result.checkpointFailures?.forEach { bucketName ->
                deleteBucket(bucketName)
            }
            result.ready = false
            return result
        }

        val bucketNames =
            targetCheckpoint.checksums
                .let {
                    if (partialPriority == null) {
                        it
                    } else {
                        it.filter { cs -> cs.priority >= partialPriority }
                    }
                }.map { it.bucket }

        db.writeTransaction { tx ->
            tx.execute(
                "UPDATE ps_buckets SET last_op = ? WHERE name IN (SELECT json_each.value FROM json_each(?))",
                listOf(targetCheckpoint.lastOpId, JsonUtil.json.encodeToString(bucketNames)),
            )

            if (partialPriority == null && targetCheckpoint.writeCheckpoint != null) {
                tx.execute(
                    "UPDATE ps_buckets SET last_op = ? WHERE name = '\$local'",
                    listOf(targetCheckpoint.writeCheckpoint),
                )
            }
        }

        val valid = updateObjectsFromBuckets(targetCheckpoint, partialPriority)

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

    private suspend fun validateChecksums(
        checkpoint: Checkpoint,
        priority: BucketPriority? = null,
    ): SyncLocalDatabaseResult {
        val serializedCheckpoint =
            JsonUtil.json.encodeToString(
                when (priority) {
                    null -> checkpoint
                    // Only validate buckets with a priority included in this partial sync.
                    else -> checkpoint.copy(checksums = checkpoint.checksums.filter { it.priority >= priority })
                },
            )

        val res =
            db.getOptional(
                "SELECT powersync_validate_checkpoint(?) AS result",
                parameters = listOf(serializedCheckpoint),
                mapper = { cursor ->
                    cursor.getString(0)!!
                },
            )
                ?: // no result
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
    private suspend fun updateObjectsFromBuckets(
        checkpoint: Checkpoint,
        priority: BucketPriority? = null,
    ): Boolean {
        @Serializable
        data class SyncLocalArgs(
            val priority: BucketPriority,
            val buckets: List<String>,
        )

        val args =
            if (priority != null) {
                JsonUtil.json.encodeToString(
                    SyncLocalArgs(
                        priority = priority,
                        buckets = checkpoint.checksums.filter { it.priority >= priority }.map { it.bucket },
                    ),
                )
            } else {
                ""
            }

        return db.writeTransaction { tx ->
            tx.execute(
                "INSERT INTO powersync_operations(op, data) VALUES(?, ?)",
                listOf("sync_local", args),
            )

            val res =
                tx.get("select last_insert_rowid()") { cursor ->
                    cursor.getLong(0)!!
                }

            val didApply = res == 1L
            if (didApply && priority == null) {
                // Reset progress counters. We only do this for a complete sync, as we want a download progress to
                // always cover a complete checkpoint instead of resetting for partial completions.
                tx.execute("""
                    UPDATE ps_buckets SET count_since_last = 0, count_at_last = ?1->name
                      WHERE ?1->name IS NOT NULL
                """.trimIndent(), listOf(JsonUtil.json.encodeToString(buildMap<String, Int> {
                    for (bucket in checkpoint.checksums) {
                        bucket.count?.let { put(bucket.bucket, it) }
                    }
                })))
            }

            return@writeTransaction didApply
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
                "INSERT INTO powersync_operations(op, data) VALUES (?, ?)",
                listOf("delete_pending_buckets", ""),
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
                listOf("clear_remove_ops", ""),
            )
        }
        this.compactCounter = 0
    }

    @Suppress("UNUSED_PARAMETER")
    override fun setTargetCheckpoint(checkpoint: Checkpoint) {
        // No-op for now
    }
}
