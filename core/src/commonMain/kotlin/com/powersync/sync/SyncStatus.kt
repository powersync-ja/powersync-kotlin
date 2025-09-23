package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
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
    /**
     * true if currently connected.
     *
     * This means the PowerSync connection is ready to download, and [PowerSyncBackendConnector.uploadData] may be called for any local changes.
     */
    public abstract val connected: Boolean

    /**
     * true if the PowerSync connection is busy connecting.
     *
     * During this stage, [PowerSyncBackendConnector.uploadData] may already be called, and [uploading] may be true.
     */
    public abstract val connecting: Boolean

    /**
     * true if actively downloading changes.
     *
     * This is only true when [connected] is also true.
     */
    public abstract val downloading: Boolean

    /**
     * Realtime progress information about downloaded operations during an active sync.
     *
     *
     * For more information on what progress is reported, see [SyncDownloadProgress].
     * This value will be non-null only if [downloading] is true.
     */
    public abstract val downloadProgress: SyncDownloadProgress?

    /**
     * true if uploading changes
     */
    public abstract val uploading: Boolean

    /**
     * Time that a last sync has fully completed, if any.
     *
     * Currently this is reset to null after a restart.
     */
    public abstract val lastSyncedAt: Instant?

    /**
     * Indicates whether there has been at least one full sync, if any.
     *
     * Is null when unknown, for example when state is still being loaded from the database.
     */
    public abstract val hasSynced: Boolean?

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
    public abstract val anyError: Any?

    /**
     * Available [PriorityStatusEntry] reporting the sync status for buckets within priorities.
     *
     * When buckets with different priorities are defined, this may contain entries before [hasSynced]
     * and [lastSyncedAt] are set to indicate that a partial (but no complete) sync has completed.
     * A completed [PriorityStatusEntry] at one priority level always includes all higher priorities too.
     */
    public abstract val priorityStatusEntries: List<PriorityStatusEntry>

    internal abstract val internalSubscriptions: List<CoreActiveStreamSubscription>?

    /**
     * Status information for whether buckets in [priority] have been synchronized.
     */
    public fun statusForPriority(priority: StreamPriority): PriorityStatusEntry {
        val byDescendingPriorities = priorityStatusEntries.sortedByDescending { it.priority }

        for (entry in byDescendingPriorities) {
            // Lower-priority buckets are synchronized after higher-priority buckets, so we look for the first
            // entry that doesn't have a higher priority.
            if (entry.priority <= priority) {
                return entry
            }
        }

        // A complete sync necessarily includes all priorities.
        return PriorityStatusEntry(priority, lastSyncedAt, hasSynced)
    }

    /**
     * All sync streams currently being tracked in the database.
     *
     * This returns null when the database is currently being opened and we don't have reliable
     * information about included streams yet.
     */
    @ExperimentalPowerSyncAPI
    public val syncStreams: List<SyncStreamStatus>? get() = internalSubscriptions?.map(this::exposeStreamStatus)

    /**
     * Status information for [stream], if it's a stream that is currently tracked by the sync
     * client.
     */
    @ExperimentalPowerSyncAPI
    public fun forStream(stream: SyncStreamDescription): SyncStreamStatus? {
        val raw = internalSubscriptions?.firstOrNull { it.name == stream.name && it.parameters == stream.parameters } ?: return null
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
}

internal data class SyncStatusDataContainer(
    override val connected: Boolean = false,
    override val connecting: Boolean = false,
    override val downloading: Boolean = false,
    override val downloadProgress: SyncDownloadProgress? = null,
    override val uploading: Boolean = false,
    override val lastSyncedAt: Instant? = null,
    override val hasSynced: Boolean? = null,
    override val uploadError: Any? = null,
    override val downloadError: Any? = null,
    override val priorityStatusEntries: List<PriorityStatusEntry> = emptyList(),
    override val internalSubscriptions: List<CoreActiveStreamSubscription> = emptyList(),
) : SyncStatusData() {
    override val anyError
        get() = downloadError ?: uploadError

    internal fun applyCoreChanges(status: CoreSyncStatus): SyncStatusDataContainer {
        val completeSync = status.priorityStatus.firstOrNull { it.priority == StreamPriority.FULL_SYNC_PRIORITY }

        return copy(
            connected = status.connected,
            connecting = status.connecting,
            downloading = status.downloading != null,
            downloadProgress = status.downloading?.let { SyncDownloadProgress(it.buckets) },
            lastSyncedAt = completeSync?.lastSyncedAt,
            hasSynced = completeSync != null,
            priorityStatusEntries =
                status.priorityStatus.map {
                    PriorityStatusEntry(
                        priority = it.priority,
                        lastSyncedAt = it.lastSyncedAt,
                        hasSynced = it.hasSynced,
                    )
                },
            internalSubscriptions = status.streams,
        )
    }

    @LegacySyncImplementation
    internal fun abortedDownload() =
        copy(
            downloading = false,
            downloadProgress = null,
        )

    @LegacySyncImplementation
    internal fun copyWithCompletedDownload() =
        copy(
            lastSyncedAt = Clock.System.now(),
            downloading = false,
            downloadProgress = null,
            hasSynced = true,
            downloadError = null,
        )
}

@ConsistentCopyVisibility
public data class SyncStatus internal constructor(
    private var data: SyncStatusDataContainer = SyncStatusDataContainer(),
) : SyncStatusData() {
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

    internal suspend fun trackOther(source: SyncStatus) {
        source.stateFlow.collect {
            update { it }
        }
    }

    override val anyError: Any?
        get() = data.anyError

    override val connected: Boolean
        get() = data.connected

    override val connecting: Boolean
        get() = data.connecting

    override val downloading: Boolean
        get() = data.downloading

    override val downloadProgress: SyncDownloadProgress?
        get() = data.downloadProgress

    override val uploading: Boolean
        get() = data.uploading

    override val lastSyncedAt: Instant?
        get() = data.lastSyncedAt

    override val hasSynced: Boolean?
        get() = data.hasSynced

    override val uploadError: Any?
        get() = data.uploadError

    override val downloadError: Any?
        get() = data.downloadError

    override val priorityStatusEntries: List<PriorityStatusEntry>
        get() = data.priorityStatusEntries

    override val internalSubscriptions: List<CoreActiveStreamSubscription>
        get() = data.internalSubscriptions

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
