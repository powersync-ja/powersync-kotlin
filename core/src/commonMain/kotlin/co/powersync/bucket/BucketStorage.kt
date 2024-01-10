package co.powersync.bucket

import co.powersync.db.PsDatabase
import co.powersync.invalidSqliteCharacters
import co.powersync.sync.SyncDataBatch
import co.powersync.sync.SyncLocalDatabaseResult

class BucketStorage(val db: PsDatabase) {

    val tableNames: Set<String> = setOf()

    init {
    }

    fun hasCrud(): Boolean {
        // TODO: Implement
        return true;
    }

    suspend fun updateLocalTarget(checkpointCallback: suspend () -> String): Boolean {
        // TODO: Implement
        return true;
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

    suspend fun saveSyncData(syncDataBatch: SyncDataBatch) {
        TODO("Not implemented yet")
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