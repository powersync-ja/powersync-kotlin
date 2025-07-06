package com.powersync.bucket

import com.powersync.db.crud.CrudEntry
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.db.schema.SerializableSchema
import com.powersync.sync.Instruction
import com.powersync.sync.LegacySyncImplementation
import com.powersync.sync.SyncDataBatch
import com.powersync.sync.SyncLocalDatabaseResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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

    suspend fun control(args: PowerSyncControlArguments): List<Instruction>
}

internal sealed interface PowerSyncControlArguments {
    @Serializable
    class Start(
        val parameters: JsonObject,
        val schema: SerializableSchema
    ) : PowerSyncControlArguments

    data object Stop : PowerSyncControlArguments

    data class TextLine(
        val line: String,
    ) : PowerSyncControlArguments

    class BinaryLine(
        val line: ByteArray,
    ) : PowerSyncControlArguments {
        override fun toString(): String = "BinaryLine"
    }

    data object CompletedUpload : PowerSyncControlArguments
}

@Serializable
internal class StartSyncIteration(
    val parameters: JsonObject,
)
