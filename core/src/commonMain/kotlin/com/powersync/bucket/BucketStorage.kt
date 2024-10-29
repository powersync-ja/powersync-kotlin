package com.powersync.bucket

import com.powersync.db.crud.CrudEntry
import com.powersync.sync.SyncDataBatch
import com.powersync.sync.SyncLocalDatabaseResult

internal interface BucketStorage {
    fun getMaxOpId(): String

    suspend fun getClientId(): String

    suspend fun nextCrudItem(): CrudEntry?

    suspend fun hasCrud(): Boolean

    suspend fun updateLocalTarget(checkpointCallback: suspend () -> String): Boolean

    suspend fun saveSyncData(syncDataBatch: SyncDataBatch)

    suspend fun getBucketStates(): List<BucketState>

    suspend fun removeBuckets(bucketsToDelete: List<String>)

    suspend fun hasCompletedSync(): Boolean

    suspend fun syncLocalDatabase(targetCheckpoint: Checkpoint): SyncLocalDatabaseResult

    fun setTargetCheckpoint(checkpoint: Checkpoint)
}
