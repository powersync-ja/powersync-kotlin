package co.powersync.bucket

import co.powersync.db.PowerSyncDatabase
import co.powersync.db.crud.CrudBatch
import co.powersync.db.crud.CrudEntry
import co.powersync.db.crud.CrudRow
import co.powersync.sync.SyncDataBatch
import co.powersync.sync.SyncLocalDatabaseResult
import co.touchlab.stately.concurrency.AtomicBoolean
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.benasher44.uuid.uuid4

class BucketStorage(val db: PowerSyncDatabase) {

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
        readTableNames()
    }

    private fun readTableNames() {
        tableNames.clear()
        // Query to get existing table names
        val names = db.createQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name GLOB 'ps_data_*'",
            mapper = { cursor ->
                cursor.getString(0)!!
            }
        ).executeAsList()

        tableNames.addAll(names)

        println("[BucketStorage::tableNames] Found tables: $tableNames")
    }

    fun startSession() {
        checksumCache = null;
    }

    fun hasCrud(): Boolean {
        return db.createQuery("SELECT 1 FROM ps_crud LIMIT 1", mapper = { cursor ->
            cursor.getLong(0) == 1L
        }).executeAsOneOrNull() ?: false
    }

    /**
     * For tests only. Others should use [PowerSyncDatabase.getCrudBatch].
     */
    suspend fun getCrudBatch(limit: Int = 100): CrudBatch? {
        if (!hasCrud()) {
            return null
        }

        val entries = db.createQuery(
            "SELECT * FROM ps_crud ORDER BY id ASC LIMIT ?",
            parameters = 1,
            binders = { bindLong(0, limit.toLong()) },
            mapper = { cursor ->
                CrudEntry.fromRow(
                    CrudRow(
                        id = cursor.getString(0)!!,
                        data = cursor.getString(1)!!,
                        txId = cursor.getLong(2)?.toInt()
                    )
                )
            }
        ).executeAsList()

        if (entries.isEmpty()) {
            return null
        }

        val last = entries.last()
        return CrudBatch(crud = entries, hasMore = true, complete = { writeCheckpoint ->
            db.writeTransaction {
                db.createQuery(
                    "DELETE FROM ps_crud WHERE id <= ?",
                    parameters = 1,
                    binders = { bindString(0, last.id) }
                ).executeAsOneOrNull()

                if (writeCheckpoint != null && hasCrud()) {
                    db.createQuery(
                        "UPDATE ps_buckets SET target_op = ? WHERE name='\$local'",
                        parameters = 1,
                        binders = { bindString(0, writeCheckpoint) }
                    ).executeAsOneOrNull()
                } else {
                    db.createQuery(
                        "UPDATE ps_buckets SET target_op = ? WHERE name='\$local'",
                        parameters = 1,
                        binders = { bindString(0, MAX_OP_ID) }
                    ).executeAsOneOrNull()
                }
            }
        })
    }

    suspend fun updateLocalTarget(checkpointCallback: suspend () -> String): Boolean {
        db.createQuery(
            "SELECT target_op FROM ps_buckets WHERE name = '\$local' AND target_op = ?",
            binders = {
                bindString(0, MAX_OP_ID)

            },
            mapper = { cursor -> cursor.getLong(0)!! }).executeAsOneOrNull()
            ?: // Nothing to update
            return false

        val seqBefore = db.createQuery(
            "SELECT seq FROM sqlite_sequence WHERE name = 'ps_crud'",
            mapper = { cursor ->
                cursor.getLong(0)!!
            }).executeAsOneOrNull() ?: // Nothing to update
        return false

        val opId = checkpointCallback()

        println("[updateLocalTarget] Updating target to checkpoint $opId")


        return db.writeTransaction {
            if (!hasCrud()) {
                println("[updateLocalTarget] ps_crud is empty")
                return@writeTransaction false
            }

            val seqAfter = db.createQuery(
                "SELECT seq FROM sqlite_sequence WHERE name = 'ps_crud'",
                mapper = { cursor ->
                    cursor.getLong(0)!!
                }).executeAsOneOrNull()
                ?: // assert isNotEmpty
                throw AssertionError("Sqlite Sequence should not be empty")

            if (seqAfter != seqBefore) {
                // New crud data may have been uploaded since we got the checkpoint. Abort.
                return@writeTransaction false;
            }

            db.createQuery("UPDATE ps_buckets SET target_op = ? WHERE name='\$local'", binders = {
                bindString(0, opId)
            }, parameters = 1, mapper = { cursor -> cursor.getLong(0)!! }).executeAsOne()

            return@writeTransaction true
        }
    }

    suspend fun saveSyncData(syncDataBatch: SyncDataBatch) {
        db.writeTransaction {
            val jsonString = Json.encodeToString(syncDataBatch);
            db.createQuery(
                "INSERT INTO powersync_operations(op, data) VALUES(?, ?)",
                parameters = 2,
                binders = {
                    bindString(0, "save")
                    bindString(1, jsonString)
                }
            ).executeAsOneOrNull()
        }
        this.compactCounter += syncDataBatch.buckets.map { it.data.size }.sum()
    }

    suspend fun getBucketStates(): List<BucketState> {
        return db.createQuery(
            "SELECT name as bucket, cast(last_op as TEXT) as op_id FROM ps_buckets WHERE pending_delete = 0",
            mapper = { cursor ->
                BucketState(
                    bucket = cursor.getString(0)!!,
                    opId = cursor.getString(1)!!
                )
            }).executeAsList()
    }

    suspend fun removeBuckets(bucketsToDelete: List<String>) {
        bucketsToDelete.forEach { bucketName ->
            deleteBucket(bucketName)
        }
    }


    suspend fun deleteBucket(bucketName: String) {
        val newName = "\$delete_${bucketName}_${uuid4()}";

        db.writeTransaction {
            db.createQuery(
                "UPDATE ps_oplog SET op=3, data=NULL WHERE op=1 AND superseded=0 AND bucket=?",
                parameters = 1,
                binders = {
                    bindString(0, bucketName)
                }).executeAsOneOrNull()

            // Rename bucket
            db.createQuery(
                "UPDATE ps_oplog SET bucket=? WHERE bucket=?",
                parameters = 2,
                binders = {
                    bindString(0, newName)
                    bindString(1, bucketName)
                }).executeAsOneOrNull()

            db.createQuery("DELETE FROM ps_buckets WHERE name = ?", parameters = 1, binders = {
                bindString(0, bucketName)
            }).executeAsOneOrNull()
        }

        this.pendingBucketDeletes.value = true;
    }

    suspend fun hasCompletedSync(): Boolean {
        if (hasCompletedSync.value) {
            return true
        }

        val completedSync = db.createQuery(
            "SELECT name, last_applied_op FROM ps_buckets WHERE last_applied_op > 0 LIMIT 1",
            mapper = { cursor ->
                cursor.getString(0)!!
            }).executeAsOneOrNull()

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
            db.createQuery(
                "UPDATE ps_buckets SET last_op = ? WHERE name IN (SELECT json_each.value FROM json_each(?))",
                parameters = 2,
                binders = {
                    bindString(0, targetCheckpoint.lastOpId)
                    bindString(1, Json.encodeToString(bucketNames))
                }).executeAsOneOrNull()

            if (targetCheckpoint.writeCheckpoint != null) {
                db.createQuery(
                    "UPDATE ps_buckets SET last_op = ? WHERE name = '\$local'",
                    parameters = 1,
                    binders = {
                        bindString(0, targetCheckpoint.writeCheckpoint)
                    }).executeAsOneOrNull()
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
        val res = db.createQuery(
            "SELECT powersync_validate_checkpoint(?) as result",
            parameters = 1,
            binders = {
                bindString(0, Json.encodeToString(checkpoint))
            },
            mapper = { cursor ->
                cursor.getString(0)!!
            }).executeAsOneOrNull() ?: //no result
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
        return db.writeTransaction {

            val res = db.driver.execute(
                null,
                "INSERT INTO powersync_operations(op, data) VALUES(?, ?)",
                parameters = 2,
                binders = {
                    bindString(0, "sync_local")
                    bindString(1, "")
                })
            return@writeTransaction res.value == 1L
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
            db.createQuery(
                "DELETE FROM ps_oplog WHERE bucket IN (SELECT name FROM ps_buckets WHERE pending_delete = 1 AND last_applied_op = last_op AND last_op >= target_op)",
            ).executeAsOneOrNull()

            db.createQuery(
                "DELETE FROM ps_buckets WHERE pending_delete = 1 AND last_applied_op = last_op AND last_op >= target_op",
            ).executeAsOneOrNull()
            // Executed once after start-up, and again when there are pending deletes.
            pendingBucketDeletes.value = false;
        }
    }

    private suspend fun clearRemoveOps() {
        if (this.compactCounter < COMPACT_OPERATION_INTERVAL) {
            return;
        }

        db.writeTransaction {
            db.createQuery(
                "INSERT INTO powersync_operations(op, data) VALUES (?, ?)",
                parameters = 2,
                binders = {
                    bindString(0, "clear_remove_ops")
                    bindString(1, "")
                }).executeAsOneOrNull()
        }
        this.compactCounter = 0;
    }

    fun setTargetCheckpoint(checkpoint: Checkpoint) {
        // No-op for now
    }
}