package co.powersync.bucket

import co.powersync.db.PowerSyncDatabase
import co.powersync.db.crud.CrudBatch
import co.powersync.invalidSqliteCharacters
import co.powersync.sync.SyncDataBatch
import co.powersync.sync.SyncLocalDatabaseResult
import co.touchlab.stately.concurrency.AtomicBoolean

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
        db.createQuery(
            "readTableNames",
            "SELECT name FROM sqlite_master WHERE type='table' AND name GLOB 'ps_data_*'"
        ) { cursor ->
            val name = cursor.getString(0)!!
            tableNames.add(name)
        }.executeAsList()
    }

    fun startSession() {
        _checksumCache = null;
    }

    fun hasCrud(): Boolean {
        // TODO: Implement
        return db.createQuery("hasCrud", "SELECT 1 FROM ps_crud LIMIT 1") { cursor ->
            cursor.getLong(0) == 1L
        }.executeAsOne()
    }

    suspend fun getCrudBatch(): CrudBatch? {
        TODO("Not implemented yet")
    }

    suspend fun updateLocalTarget(checkpointCallback: suspend () -> String): Boolean {
        // TODO: Implement
        return true;
    }

    suspend fun saveSyncData(syncDataBatch: SyncDataBatch) {
        TODO("Not implemented yet")
    }

    suspend fun getBucketStates(): List<BucketState> {
        return listOf()
    }

    suspend fun removeBuckets(bucketsToDelete: List<String>) {
        bucketsToDelete.forEach { bucketName ->
            deleteBucket(bucketName)
        }
    }


    suspend fun deleteBucket(bucketName: String) {
        TODO("Not implemented yet")
    }


    fun setTargetCheckpoint(checkpoint: Checkpoint) {
        // No-op for now
    }

    suspend fun hasCompletedSync(): Boolean {
        return true
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