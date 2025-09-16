package com.powersync.bucket

import com.powersync.db.SqlCursor
import com.powersync.db.crud.CrudEntry
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.db.schema.SerializableSchema
import com.powersync.sync.Instruction
import com.powersync.sync.LegacySyncImplementation
import com.powersync.sync.SyncDataBatch
import com.powersync.sync.SyncLocalDatabaseResult
import com.powersync.utils.JsonUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal interface BucketStorage {
    fun getMaxOpId(): String

    suspend fun getClientId(): String

    suspend fun nextCrudItem(): CrudEntry?

    fun nextCrudItem(transaction: PowerSyncTransaction): CrudEntry?

    suspend fun hasCrud(): Boolean

    fun hasCrud(transaction: PowerSyncTransaction): Boolean

    fun mapCrudEntry(row: SqlCursor): CrudEntry

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
    /**
     * Returns the arguments for the `powersync_control` SQL invocation.
     */
    val sqlArguments: Pair<String, Any?>

    @Serializable
    class Start(
        val parameters: JsonObject,
        val schema: SerializableSchema,
    ) : PowerSyncControlArguments {
        override val sqlArguments: Pair<String, Any?>
            get() = "start" to JsonUtil.json.encodeToString(this)
    }

    data object Stop : PowerSyncControlArguments {
        override val sqlArguments: Pair<String, Any?> = "stop" to null
    }

    data class TextLine(
        val line: String,
    ) : PowerSyncControlArguments {
        override val sqlArguments: Pair<String, Any?> = "line_text" to line
    }

    class BinaryLine(
        line: ByteArray,
    ) : PowerSyncControlArguments {
        override fun toString(): String = "BinaryLine"

        override val sqlArguments: Pair<String, Any?> = "line_binary" to line
    }

    data object CompletedUpload : PowerSyncControlArguments {
        override val sqlArguments: Pair<String, Any?> = "completed_upload" to null
    }

    data object ConnectionEstablished : PowerSyncControlArguments {
        override val sqlArguments: Pair<String, Any?> = "connection" to "established"
    }

    data object ResponseStreamEnd : PowerSyncControlArguments {
        override val sqlArguments: Pair<String, Any?> = "connection" to "end"
    }
}

@Serializable
internal class StartSyncIteration(
    val parameters: JsonObject,
)
