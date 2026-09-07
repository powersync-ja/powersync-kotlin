package com.powersync.bucket

import co.touchlab.kermit.Logger
import co.touchlab.stately.concurrency.AtomicBoolean
import com.powersync.db.SqlCursor
import com.powersync.db.StreamKey
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudRow
import com.powersync.db.internal.InternalDatabase
import com.powersync.db.internal.InternalTable
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.db.schema.Schema
import com.powersync.sync.CoreSyncStatus
import com.powersync.sync.Instruction
import com.powersync.utils.JsonUtil
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

internal class BucketStorage(
    private val db: InternalDatabase,
    private val logger: Logger,
) {
    private var hasCompletedSync = AtomicBoolean(false)

    suspend fun getClientId(): String {
        val id =
            db.getOptional("SELECT powersync_client_id() as client_id") {
                it.getString(0)!!
            }
        return id ?: throw IllegalStateException("Client ID not found")
    }

    suspend fun nextCrudItem(): CrudEntry? = db.getOptional(sql = nextCrudQuery, mapper = ::mapCrudEntry)

    suspend fun nextCrudItem(transaction: PowerSyncTransaction): CrudEntry? =
        transaction.getOptionalAsync(sql = nextCrudQuery, mapper = ::mapCrudEntry)

    private val nextCrudQuery = "SELECT id, tx_id, data FROM ${InternalTable.CRUD} ORDER BY id ASC LIMIT 1"

    fun mapCrudEntry(row: SqlCursor): CrudEntry =
        CrudEntry.fromRow(
            CrudRow(
                id = row.getString(0)!!,
                txId = row.getString(1)?.toInt(),
                data = row.getString(2)!!,
            ),
        )

    suspend fun hasCrud(): Boolean {
        val res = db.getOptional(sql = hasCrudQuery, mapper = hasCrudMapper)
        return res == 1L
    }

    suspend fun hasCrud(transaction: PowerSyncTransaction): Boolean {
        val res = transaction.getOptionalAsync(sql = hasCrudQuery, mapper = hasCrudMapper)
        return res == 1L
    }

    private val hasCrudQuery = "SELECT 1 FROM ps_crud LIMIT 1"
    private val hasCrudMapper: (SqlCursor) -> Long = {
        it.getLong(0)!!
    }

    suspend fun updateLocalTarget(checkpointCallback: suspend () -> Long): Boolean {
        val existingCheckpointRequest = db.readTransactionAsync { it.targetCheckpointRequestId() }
        if (existingCheckpointRequest != MAX_OP_ID) {
            // Nothing to update
            return false
        }

        val seqBefore =
            db.getOptional("SELECT seq FROM main.sqlite_sequence WHERE name = '${InternalTable.CRUD}'") {
                it.getLong(0)!!
            } ?: // Nothing to update
                return false

        val opId = checkpointCallback()

        logger.i { "[updateLocalTarget] Updating target to checkpoint $opId" }

        return db.writeTransactionAsync { tx ->
            if (hasCrud(tx)) {
                logger.w { "[updateLocalTarget] ps crud is not empty" }
                return@writeTransactionAsync false
            }

            val seqAfter =
                tx.getOptionalAsync("SELECT seq FROM main.sqlite_sequence WHERE name = '${InternalTable.CRUD}'") {
                    it.getLong(0)!!
                }
                    ?: // assert isNotEmpty
                    throw AssertionError("Sqlite Sequence should not be empty")

            if (seqAfter != seqBefore) {
                logger.d("seqAfter != seqBefore seqAfter: $seqAfter seqBefore: $seqBefore")
                // New crud data may have been uploaded since we got the checkpoint. Abort.
                return@writeTransactionAsync false
            }

            tx.targetCheckpointRequestId(opId)
            return@writeTransactionAsync true
        }
    }

    suspend fun hasCompletedSync(): Boolean {
        if (hasCompletedSync.value) {
            return true
        }

        val completedSync =
            db.getOptional(
                "SELECT powersync_last_synced_at()",
                mapper = { cursor ->
                    cursor.getString(0)!!
                },
            )

        return if (completedSync != null) {
            hasCompletedSync.value = true
            true
        } else {
            false
        }
    }

    private fun handleControlResult(cursor: SqlCursor): List<Instruction> {
        val result = cursor.getString(0)!!
        logger.v { "control result: $result" }

        return JsonUtil.json.decodeFromString<List<Instruction>>(result)
    }

    suspend fun control(args: PowerSyncControlArguments): List<Instruction> =
        db.writeTransactionAsync { tx ->
            logger.v { "powersync_control: $args" }

            val (op: String, data: Any?) = args.sqlArguments
            tx.getAsync("SELECT powersync_control(?, ?) AS r", listOf(op, data), ::handleControlResult)
        }

    suspend fun resolveOfflineSyncStatus(): CoreSyncStatus =
        db.get("SELECT powersync_offline_sync_status()") {
            JsonUtil.json.decodeFromString<CoreSyncStatus>(it.getString(0)!!)
        }

    suspend fun readOrUpdateCheckpoint(
        variant: String,
        update: Long? = null,
    ): Long? = db.writeTransactionAsync { tx -> tx.readOrUpdateCheckpoint(variant, update) }

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
