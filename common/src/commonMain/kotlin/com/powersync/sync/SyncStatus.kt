package com.powersync.sync

import com.powersync.bucket.StreamPriority
import com.powersync.connectors.PowerSyncBackendConnector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Clock
import kotlin.time.Instant

@ConsistentCopyVisibility
public data class PriorityStatusEntry internal constructor(
    val priority: StreamPriority,
    val lastSyncedAt: Instant?,
    val hasSynced: Boolean?,
)

public sealed class SyncStatusData {
    internal abstract val core: CoreSyncStatus?

    /**
     * true if currently connected.
     *
     * This means the PowerSync connection is ready to download, and [PowerSyncBackendConnector.uploadData] may be called for any local changes.
     */
    public val connected: Boolean get() = core?.connected ?: false

    /**
     * true if the PowerSync connection is busy connecting.
     *
     * During this stage, [PowerSyncBackendConnector.uploadData] may already be called, and [uploading] may be true.
     */
    public val connecting: Boolean get() = core?.connecting ?: false

    /**
     * true if actively downloading changes.
     *
     * This is only true when [connected] is also true.
     */
    public val downloading: Boolean get() = core?.downloading != null

    /**
     * Realtime progress information about downloaded operations during an active sync.
     *
     *
     * For more information on what progress is reported, see [SyncDownloadProgress].
     * This value will be non-null only if [downloading] is true.
     */
    public val downloadProgress: SyncDownloadProgress? get() = core?.downloading?.let { SyncDownloadProgress(it.buckets) }

    /**
     * true if uploading changes
     */
    public abstract val uploading: Boolean

    /**
     * Time that a last sync has fully completed, if any.
     *
     * Currently this is reset to null after a restart.
     */
    public val lastSyncedAt: Instant? get() {
        return statusForPriority(StreamPriority.FULL_SYNC_PRIORITY).lastSyncedAt
    }

    /**
     * Indicates whether there has been at least one full sync, if any.
     *
     * Is null when unknown, for example when state is still being loaded from the database.
     */
    public val hasSynced: Boolean? get() {
        return statusForPriority(StreamPriority.FULL_SYNC_PRIORITY).hasSynced
    }

    /**
     * Error during uploading.
     *
     * Cleared on the next successful upload.
     */
    public abstract val uploadError: Any?

    /**
     * Error during downloading (including connecting).
     *
     * Cleared on the next successful data download.
     */
    public abstract val downloadError: Any?

    /**
     * Convenience getter for either the value of downloadError or uploadError
     */
    public val anyError: Any? get() = downloadError ?: uploadError

    /**
     * Available [PriorityStatusEntry] reporting the sync status for buckets within priorities.
     *
     * When buckets with different priorities are defined, this may contain entries before [hasSynced]
     * and [lastSyncedAt] are set to indicate that a partial (but no complete) sync has completed.
     * A completed [PriorityStatusEntry] at one priority level always includes all higher priorities too.
     */
    public val priorityStatusEntries: List<PriorityStatusEntry> get() {
        return core?.priorityStatus?.map(this::exposePriorityStatus) ?: emptyList()
    }

    /**
     * Status information for whether buckets in [priority] have been synchronized.
     */
    public fun statusForPriority(priority: StreamPriority): PriorityStatusEntry {
        val statusEntries = core?.priorityStatus ?: return PriorityStatusEntry(priority, null, null)

        // Note: statusEntries is always sorted by descending bucket priority
        for (entry in statusEntries) {
            // Lower-priority buckets are synchronized after higher-priority buckets, so we look for the first
            // entry that doesn't have a higher priority.
            if (entry.priority <= priority) {
                return exposePriorityStatus(entry)
            }
        }

        // A complete sync necessarily includes all priorities.
        return PriorityStatusEntry(priority, null, false)
    }

    /**
     * All sync streams currently being tracked in the database.
     *
     * Returns null when the database is currently being opened and we don't have reliable
     * information about included streams yet. Once non-null, an empty list means the database
     * is initialized but no streams are active.
     */
    public val syncStreams: List<SyncStreamStatus>? get() = core?.streams?.map(this::exposeStreamStatus)

    /**
     * Status information for [stream], if it's a stream that is currently tracked by the sync
     * client.
     */
    public fun forStream(stream: SyncStreamDescription): SyncStreamStatus? {
        val raw = core?.streams?.firstOrNull { it.name == stream.name && it.parameters == stream.parameters } ?: return null
        return exposeStreamStatus(raw)
    }

    private fun exposeStreamStatus(internal: CoreActiveStreamSubscription): SyncStreamStatus {
        val progress =
            if (this.downloadProgress == null) {
                null
            } else {
                // The core extension will always give us progress numbers, but we should only expose
                // them when that makes sense (i.e. we're actually downloading).
                internal.progress
            }

        return SyncStreamStatus(progress, internal)
    }

    private fun exposePriorityStatus(internal: CorePriorityStatus): PriorityStatusEntry =
        PriorityStatusEntry(
            priority = internal.priority,
            lastSyncedAt = internal.lastSyncedAt,
            hasSynced = internal.hasSynced,
        )
}

internal data class SyncStatusDataContainer(
    override val core: CoreSyncStatus?,
    override val uploading: Boolean = false,
    override val uploadError: Any? = null,
    override val downloadError: Any? = null,
) : SyncStatusData()

@ConsistentCopyVisibility
public data class SyncStatus internal constructor(
    private var data: SyncStatusDataContainer = SyncStatusDataContainer(null),
) : SyncStatusData() {
    override val core: CoreSyncStatus?
        get() = data.core

    private val stateFlow: MutableStateFlow<SyncStatusDataContainer> = MutableStateFlow(data)

    /**
     * @returns a flow which emits whenever the sync status has changed
     */
    public fun asFlow(): SharedFlow<SyncStatusData> = stateFlow.asSharedFlow()

    /**
     * Updates the internal sync status indicators and emits Flow updates
     */
    internal inline fun update(makeCopy: SyncStatusDataContainer.() -> SyncStatusDataContainer) {
        data = data.makeCopy()
        stateFlow.value = data
    }

    override val uploading: Boolean
        get() = data.uploading

    override val uploadError: Any?
        get() = data.uploadError

    override val downloadError: Any?
        get() = data.downloadError

    override fun toString(): String =
        "SyncStatus(connected=$connected, connecting=$connecting, downloading=$downloading, uploading=$uploading, lastSyncedAt=$lastSyncedAt, hasSynced=$hasSynced, error=$anyError)"

    public companion object {
        public fun empty(): SyncStatus = SyncStatus()
    }
}

/**
 * Current information about a [SyncStreamSubscription].
 */
@ConsistentCopyVisibility
public data class SyncStreamStatus internal constructor(
    /**
     * If the sync status is currently downloading, information about download progress related to
     * this stream.
     */
    val progress: ProgressWithOperations?,
    internal val internal: CoreActiveStreamSubscription,
) {
    /**
     * The [SyncSubscriptionDescription] providing information about the subscription.
     */
    val subscription: SyncSubscriptionDescription
        get() = internal

    /**
     * The priority of this stream.
     *
     * New data on higher-priority streams can interrupt low-priority streams.
     */
    val priority: StreamPriority
        get() = internal.priority ?: StreamPriority.FULL_SYNC_PRIORITY
}
