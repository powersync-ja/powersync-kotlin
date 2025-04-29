package com.powersync.bucket

import com.powersync.db.crud.CrudEntry
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.sync.Instruction
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

    suspend fun control(op: String, payload: String?): List<Instruction>
    suspend fun control(op: String, payload: ByteArray): List<Instruction>
}
