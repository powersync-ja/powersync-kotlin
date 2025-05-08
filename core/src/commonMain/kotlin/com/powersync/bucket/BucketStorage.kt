package com.powersync.bucket

import com.powersync.db.crud.CrudEntry
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.sync.Instruction
import com.powersync.sync.LegacySyncImplementation
import com.powersync.sync.SyncDataBatch
import com.powersync.sync.SyncLocalDatabaseResult

internal interface BucketStorage {
    fun getMaxOpId(): String

    suspend fun getClientId(): String

    suspend fun nextCrudItem(): CrudEntry?

    fun nextCrudItem(transaction: PowerSyncTransaction): CrudEntry?

    fun getCrudItemsByTransactionId(
        transactionId: Int,
        transaction: PowerSyncTransaction,
    ): List<CrudEntry>

    suspend fun hasCrud(): Boolean

    fun hasCrud(transaction: PowerSyncTransaction): Boolean

    suspend fun updateLocalTarget(checkpointCallback: suspend () -> String): Boolean

    suspend fun hasCompletedSync(): Boolean

    @LegacySyncImplementation
    suspend fun getBucketStates(): List<BucketState>
    @LegacySyncImplementation
    suspend fun getBucketOperationProgress(): Map<String, LocalOperationCounters>
    @LegacySyncImplementation
    suspend fun removeBuckets(bucketsToDelete: List<String>)
    @LegacySyncImplementation
    fun setTargetCheckpoint(checkpoint: Checkpoint)
    @LegacySyncImplementation
    suspend fun saveSyncData(syncDataBatch: SyncDataBatch)
    @LegacySyncImplementation
    suspend fun syncLocalDatabase(
        targetCheckpoint: Checkpoint,
        partialPriority: BucketPriority? = null,
    ): SyncLocalDatabaseResult

    suspend fun control(op: String, payload: String?): List<Instruction>
    suspend fun control(op: String, payload: ByteArray): List<Instruction>
}
