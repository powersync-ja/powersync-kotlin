package co.powersync.bucket

import co.powersync.db.PowerSyncDatabase
import co.powersync.db.crud.CrudBatch
import co.powersync.db.crud.CrudEntry
import co.powersync.db.crud.CrudRow
import co.powersync.invalidSqliteCharacters
import co.powersync.sync.SyncDataBatch
import co.powersync.sync.SyncDataBucket
import co.powersync.sync.SyncLocalDatabaseResult
import co.touchlab.stately.concurrency.AtomicBoolean
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.benasher44.uuid.uuid4

class BucketStorage(val db: PowerSyncDatabase) {

    private val tableNames: MutableSet<String> = mutableSetOf()

    private var _hasCompletedSync = AtomicBoolean(false)
    private var pendingBucketDeletes = AtomicBoolean(false)
    private var _checksumCache: ChecksumCache? = null

    companion object {
        const val MAX_OP_ID = "9223372036854775807"
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
    }

    fun startSession() {
        _checksumCache = null;
    }

    fun hasCrud(): Boolean {
        return db.createQuery("SELECT 1 FROM ps_crud LIMIT 1", mapper = { cursor ->
            cursor.getLong(0) == 1L
        }).executeAsOneOrNull() ?: false
    }

    /**
     * For tests only. Others should use [PowerSyncDatabase.getCrudBatch].
     */
    fun getCrudBatch(limit: Int = 100): CrudBatch? {
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
            syncDataBatch.buckets.forEach {


                val jsonString = Json.encodeToString(it);

                val result = db.createQuery(
                    "INSERT INTO powersync_operations(op, data) VALUES(?, ?);",
                    parameters = 2,
                    binders = {
                        bindString(0, "save")
                        bindString(1, jsonString)
                    },
                    mapper = { cursor -> cursor.getLong(0)!! }).executeAsOneOrNull()

                println("[saveSyncData] Inserted $result rows into powersync_operations")
            }
        }
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
        if (_hasCompletedSync.value) {
            return true
        }

        val completedSync = db.createQuery(
            "SELECT name, last_applied_op FROM ps_buckets WHERE last_applied_op > 0 LIMIT 1",
            mapper = { cursor ->
                cursor.getString(0)!!
            }).executeAsOneOrNull()

        return if (completedSync != null) {
            _hasCompletedSync.value = true
            true
        } else {
            false
        }
    }

    fun setTargetCheckpoint(checkpoint: Checkpoint) {
        // No-op for now
    }

    /**
     * Get a table name for a specific type. The table may or may not exist.
     *
     * The table name must always be enclosed in "quotes" when using inside a SQL query.
     */
    fun _getTypeTableName(type: String): String {
        if (invalidSqliteCharacters.containsMatchIn(type)) {
            throw AssertionError("Invalid characters in type name: $type")
        }
        return "ps_data__$type"
    }

    suspend fun syncLocalDatabase(targetCheckpoint: Checkpoint): SyncLocalDatabaseResult {
        return SyncLocalDatabaseResult(ready = true)
    }
}