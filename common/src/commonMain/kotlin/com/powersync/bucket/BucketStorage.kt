package com.powersync.bucket

import com.powersync.db.SqlCursor
import com.powersync.db.StreamKey
import com.powersync.db.crud.CrudEntry
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.db.schema.Schema
import com.powersync.sync.CoreSyncStatus
import com.powersync.sync.Instruction
import com.powersync.utils.JsonUtil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal interface BucketStorage {
    suspend fun getClientId(): String

    suspend fun nextCrudItem(): CrudEntry?

    suspend fun nextCrudItem(transaction: PowerSyncTransaction): CrudEntry?

    suspend fun hasCrud(): Boolean

    suspend fun hasCrud(transaction: PowerSyncTransaction): Boolean

    fun mapCrudEntry(row: SqlCursor): CrudEntry

    suspend fun updateLocalTarget(checkpointCallback: suspend () -> Long): Boolean

    suspend fun hasCompletedSync(): Boolean

    suspend fun control(args: PowerSyncControlArguments): List<Instruction>

    suspend fun resolveOfflineSyncStatus(): CoreSyncStatus

    suspend fun readOrUpdateCheckpoint(
        variant: String,
        update: Long? = null,
    ): Long?

    companion object {
        const val MAX_OP_ID = 9223372036854775807L
    }
}

/**
 * Reads the current target checkpoint request id, or updates it when the update parameter
 * is set.
 *
 * The target checkpoint request is a checkpoint the sync service needs to include in a
 * `checkpoint_complete` message for new changes to be applied locally. This guards against uploaded
 * changes that have not yet been synced to flicker if we apply an intermediate checkpoint.
 *
 * [BucketStorage.MAX_OP_ID] can be used as a sentinel value in case there are pending changes that
 * have been uploaded, but for which no checkpoint request has been created yet.
 */
internal suspend fun PowerSyncTransaction.targetCheckpointRequestId(update: Long? = null): Long? = readOrUpdateCheckpoint("target", update)

internal suspend fun PowerSyncTransaction.readOrUpdateCheckpoint(
    variant: String,
    update: Long? = null,
): Long? {
    var readValue: Long? = null

    getAsync("SELECT powersync_control(?, ?)", listOf("${variant}_checkpoint_request_id", update)) { row ->
        // We can't return nullable values here, so write to the outer local
        readValue = row.getLong(0)
    }
    return readValue
}

internal sealed interface PowerSyncControlArguments {
    /**
     * Returns the arguments for the `powersync_control` SQL invocation.
     */
    val sqlArguments: Pair<String, Any?>

    @Serializable
    class Start(
        val parameters: JsonObject,
        val schema: Schema,
        @SerialName("include_defaults")
        val includeDefaults: Boolean,
        @SerialName("active_streams")
        val activeStreams: List<StreamKey>,
        @SerialName("app_metadata")
        val appMetadata: Map<String, String>,
        @SerialName("checkpoint_mode")
        val checkpointMode: String,
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

    data object DidRefreshToken : PowerSyncControlArguments {
        override val sqlArguments: Pair<String, Any?> = "refreshed_token" to null
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

    data class CheckpointSeedFailed(
        val cause: Exception,
    ) : PowerSyncControlArguments {
        override val sqlArguments: Pair<String, Any?>
            get() = throw IllegalStateException("Has no control arguments")
    }

    class UpdateSubscriptions(
        activeStreams: List<StreamKey>,
    ) : PowerSyncControlArguments {
        override val sqlArguments: Pair<String, Any?> =
            "update_subscriptions" to JsonUtil.json.encodeToString(activeStreams)
    }
}
