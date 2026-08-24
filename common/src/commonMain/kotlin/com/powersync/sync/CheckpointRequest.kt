package com.powersync.sync

import com.powersync.ExperimentalCheckpointRequestsApi
import com.powersync.db.PowerSyncDatabaseImpl
import kotlinx.coroutines.flow.first

/**
 * A checkpoint request created by [com.powersync.PowerSyncDatabase.requestCheckpoint].
 *
 * Use this value to wait until the local database has applied server-side changes up to the
 * requested checkpoint. This is useful for explicit refresh flows where the caller wants
 * confirmation that the local view has caught up to the service.
 *
 * Checkpoint requests are backed by request ids tracked in the local database, so they are reusable
 * across disconnect and reconnect cycles. A wait interrupted by a disconnect throws an error, but
 * the same request can be awaited again once a new connection is established.
 *
 * Requests do not survive {@link CommonPowerSyncDatabase#disconnectAndClear}, instances created
 * before a clear should be discarded and requested again.
 */
@ExperimentalCheckpointRequestsApi
public class CheckpointRequest internal constructor(
    private val requestId: Long,
    private val database: PowerSyncDatabaseImpl,
) {
    /**
     * Whether this checkpoint request has synced before.
     */
    public val hasSynced: Boolean get() = database.currentStatus.isCheckpointRequestApplied(requestId)

    /**
     * Waits until this checkpoint has been synced locally.
     *
     * This method fails on sync errors: If a download or upload error occurs before this checkpoint
     * request has synced, that error is rethrown here. This makes it easier to observe sync errors
     * when relying on checkpoints. Once sync has recovered, it is valid to call this method again
     * to await the checkpoint.
     */
    public suspend fun waitForSync() {
        if (hasSynced) return

        database.inspectCurrentStreamClient { client ->
            if (client == null) {
                throw CheckpointRequestException.Disconnected()
            }
            if (client.options.checkpointMode !is CheckpointMode.Requests) {
                throw CheckpointRequestException.Disabled()
            }
        }

        database.currentStatus.asFlow().first { status ->
            if (status.isCheckpointRequestApplied(requestId)) return@first true

            status.anyError?.let { error ->
                throw CheckpointRequestException.StatusError(error as? Throwable)
            }

            if (!status.connected && !status.connecting) {
                throw CheckpointRequestException.Disconnected()
            }

            false
        }
    }
}
