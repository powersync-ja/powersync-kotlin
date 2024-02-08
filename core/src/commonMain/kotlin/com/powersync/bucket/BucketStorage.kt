package com.powersync.bucket

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.powersync.db.internal.PsInternalDatabase
import com.powersync.sync.SyncDataBatch
import com.powersync.sync.SyncLocalDatabaseResult
import co.touchlab.stately.concurrency.AtomicBoolean
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.runBlocking

class BucketStorage(val db: PsInternalDatabase) {

    private val tableNames: MutableSet<String> = mutableSetOf()
    private var hasCompletedSync = AtomicBoolean(false)
    private var checksumCache: ChecksumCache? = null
    private var pendingBucketDeletes = AtomicBoolean(false)

    /**
     * Count up, and do a compact on startup.
     */
    private var compactCounter = COMPACT_OPERATION_INTERVAL;

    companion object {
        const val MAX_OP_ID = "9223372036854775807"
        const val COMPACT_OPERATION_INTERVAL = 1_000
    }

    init {
        runBlocking {
            readTableNames()
        }
    }

    private suspend fun readTableNames() {
        tableNames.clear()
        // Query to get existing table names
        val names =
            db.getAll("SELECT name FROM sqlite_master WHERE type='table' AND name GLOB 'ps_data_*'",
                mapper = { cursor ->
                    cursor.getString(0)!!
                }
            )

        tableNames.addAll(names)
        println("[BucketStorage::tableNames] Found tables: $tableNames")
    }

    fun startSession() {
        checksumCache = null;
    }

    fun getMaxOpId(): String {
        return MAX_OP_ID;
    }

    suspend fun hasCrud(): Boolean {
        return db.queries.hasCrud().awaitAsOneOrNull() == 1L
    }

    suspend fun updateLocalTarget(checkpointCallback: suspend () -> String): Boolean {
        db.getOptional(
            "SELECT target_op FROM ps_buckets WHERE name = '\$local' AND target_op = ?",
            parameters = listOf(MAX_OP_ID),
            mapper = { cursor -> cursor.getLong(0)!! }
        )
            ?: // Nothing to update
            return false

        val seqBefore = db.queries.getCrudSequence().awaitAsOneOrNull()
            ?: // Nothing to update
            return false

        val opId = checkpointCallback()

        println("[BucketStorage::updateLocalTarget] Updating target to checkpoint $opId")

        return db.readTransaction {
            if (hasCrud()) {
                println("[BucketStorage::updateLocalTarget] ps crud is not empty")
                return@readTransaction false
            }

            val seqAfter = db.queries.getCrudSequence().awaitAsOneOrNull()
                ?: // assert isNotEmpty
                throw AssertionError("Sqlite Sequence should not be empty")

            if (seqAfter != seqBefore) {
                // New crud data may have been uploaded since we got the checkpoint. Abort.
                return@readTransaction false;
            }

            val numRowsUpdated =
                db.execute("UPDATE ps_buckets SET target_op = ? WHERE name='\$local'", listOf(opId))
            println("[BucketStorage::updateLocalTarget] $numRowsUpdated updated")
            return@readTransaction true
        }
    }

    suspend fun saveSyncData(syncDataBatch: SyncDataBatch) {
        db.writeTransaction {
            val jsonString = Json.encodeToString(syncDataBatch);
            db.execute(
                "INSERT INTO powersync_operations(op, data) VALUES(?, ?)",
                listOf("save", jsonString)
            )
        }
        this.compactCounter += syncDataBatch.buckets.sumOf { it.data.size }
    }

    suspend fun getBucketStates(): List<BucketState> {
        return db.getAll(
            "SELECT name as bucket, cast(last_op as TEXT) as op_id FROM ps_buckets WHERE pending_delete = 0",
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


    suspend fun deleteBucket(bucketName: String) {
        val newName = "\$delete_${bucketName}_${uuid4()}";

        db.writeTransaction {
            db.execute(
                "UPDATE ps_oplog SET op=${OpType.REMOVE}, data=NULL WHERE op=${OpType.PUT} AND superseded=0 AND bucket=?",
                listOf(bucketName)
            )

            // Rename bucket
            db.execute(
                "UPDATE ps_oplog SET bucket=? WHERE bucket=?",
                listOf(newName, bucketName)
            )

            db.execute("DELETE FROM ps_buckets WHERE name = ?", parameters = listOf(bucketName))
        }

        this.pendingBucketDeletes.value = true;
    }

    suspend fun hasCompletedSync(): Boolean {
        if (hasCompletedSync.value) {
            return true
        }

        val completedSync = db.getOptional(
            "SELECT name, last_applied_op FROM ps_buckets WHERE last_applied_op > 0 LIMIT 1",
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
        val result = validateChecksums(targetCheckpoint);

        if (!result.checkpointValid) {
            println("[BucketStorage::SyncLocalDatabase] Checksums failed for ${result.checkpointFailures}")
            result.checkpointFailures?.forEach { bucketName ->
                deleteBucket(bucketName)
            }
            result.ready = false
            return result
        }

        val bucketNames = targetCheckpoint.checksums.map { it.bucket }

        db.writeTransaction {
            db.execute(
                "UPDATE ps_buckets SET last_op = ? WHERE name IN (SELECT json_each.value FROM json_each(?))",
                listOf(targetCheckpoint.lastOpId, Json.encodeToString(bucketNames))
            )

            if (targetCheckpoint.writeCheckpoint != null) {
                db.execute(
                    "UPDATE ps_buckets SET last_op = ? WHERE name = '\$local'",
                    listOf(targetCheckpoint.writeCheckpoint),
                )
            }
        }

        val valid = updateObjectsFromBuckets(targetCheckpoint);

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

    suspend fun validateChecksums(checkpoint: Checkpoint): SyncLocalDatabaseResult {
        val res = db.getOptional(
            "SELECT powersync_validate_checkpoint(?) as result",
            parameters = listOf(Json.encodeToString(checkpoint)),
            mapper = { cursor ->
                cursor.getString(0)!!
            })
            ?: //no result
            return SyncLocalDatabaseResult(
                ready = false,
                checkpointValid = false,
            )

        return Json.decodeFromString<SyncLocalDatabaseResult>(res);
    }

    /**
     * Atomically update the local state to the current checkpoint.
     *
     * This includes creating new tables, dropping old tables, and copying data over from the oplog.
     */
    private suspend fun updateObjectsFromBuckets(checkpoint: Checkpoint): Boolean {

        val tableNames = db.getAll(
            "SELECT row_type FROM ps_oplog WHERE op_id = ?",
            listOf(checkpoint.lastOpId.toLong()),
            mapper = { cursor ->
                cursor.getString(0)!!
            }).toSet().toTypedArray()

        return db.readTransaction {
            val res = db.execute(
                "INSERT INTO powersync_operations(op, data) VALUES(?, ?)",
                listOf("sync_local", "")
            )

            this.afterCommit {
                db.driver.notifyListeners(queryKeys = tableNames)
            }

            return@readTransaction res == 1L
        }
    }

    suspend fun forceCompact() {
        // Reset counter
        this.compactCounter = COMPACT_OPERATION_INTERVAL;
        this.pendingBucketDeletes.value = true;

        this.autoCompact();
    }


    suspend fun autoCompact() {
        // 1. Delete buckets
        deletePendingBuckets();

        // 2. Clear REMOVE operations, only keeping PUT ones
        clearRemoveOps()
    }

    private suspend fun deletePendingBuckets() {
        if (!this.pendingBucketDeletes.value) {
            return;
        }

        db.writeTransaction {
            db.execute(
                "DELETE FROM ps_oplog WHERE bucket IN (SELECT name FROM ps_buckets WHERE pending_delete = 1 AND last_applied_op = last_op AND last_op >= target_op)",
            )

            db.execute(
                "DELETE FROM ps_buckets WHERE pending_delete = 1 AND last_applied_op = last_op AND last_op >= target_op",
            )
            // Executed once after start-up, and again when there are pending deletes.
            pendingBucketDeletes.value = false;
        }
    }

    private suspend fun clearRemoveOps() {
        if (this.compactCounter < COMPACT_OPERATION_INTERVAL) {
            return;
        }

        db.writeTransaction {
            db.execute(
                "INSERT INTO powersync_operations(op, data) VALUES (?, ?)",
                listOf("clear_remove_ops", "")
            )
        }
        this.compactCounter = 0;
    }

    fun setTargetCheckpoint(checkpoint: Checkpoint) {
        // No-op for now
    }
}