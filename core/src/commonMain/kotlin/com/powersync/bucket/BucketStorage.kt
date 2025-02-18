package com.powersync.bucket

import com.powersync.db.crud.CrudEntry
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.sync.SyncDataBatch
import com.powersync.sync.SyncLocalDatabaseResult

internal interface BucketStorage {
    fun getMaxOpId(): String

    suspend fun getClientId(): String

    suspend fun nextCrudItem(): CrudEntry?

    fun nextCrudItem(transaction: PowerSyncTransaction): CrudEntry?

    suspend fun hasCrud(): Boolean

    fun hasCrud(transaction: PowerSyncTransaction): Boolean

    suspend fun updateLocalTarget(checkpointCallback: suspend () -> String): Boolean

    suspend fun saveSyncData(syncDataBatch: SyncDataBatch)

    suspend fun getBucketStates(): List<BucketState>

    suspend fun removeBuckets(bucketsToDelete: List<String>)

    suspend fun hasCompletedSync(): Boolean

    suspend fun syncLocalDatabase(targetCheckpoint: Checkpoint, partialPriority: BucketPriority? = null): SyncLocalDatabaseResult

    fun setTargetCheckpoint(checkpoint: Checkpoint)
}
