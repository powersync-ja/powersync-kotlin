package com.powersync.sync

import com.powersync.bucket.BucketPriority
import com.powersync.connectors.PowerSyncBackendConnector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@ConsistentCopyVisibility
public data class PriorityStatusEntry internal constructor(
    val priority: BucketPriority,
    val lastSyncedAt: Instant?,
    val hasSynced: Boolean?,
)

public interface SyncStatusData {
    /**
     * true if currently connected.
     *
     * This means the PowerSync connection is ready to download, and [PowerSyncBackendConnector.uploadData] may be called for any local changes.
     */
    public val connected: Boolean

    /**
     * true if the PowerSync connection is busy connecting.
     *
     * During this stage, [PowerSyncBackendConnector.uploadData] may already be called, and [uploading] may be true.
     */
    public val connecting: Boolean

    /**
     * true if actively downloading changes.
     *
     * This is only true when [connected] is also true.
     */
    public val downloading: Boolean

    /**
     * Realtime progress information about downloaded operations during an active sync.
     *
     *
     * For more information on what progress is reported, see [SyncDownloadProgress].
     * This value will be non-null only if [downloading] is true.
     */
    public val downloadProgress: SyncDownloadProgress?

    /**
     * true if uploading changes
     */
    public val uploading: Boolean

    /**
     * Time that a last sync has fully completed, if any.
     *
     * Currently this is reset to null after a restart.
     */
    public val lastSyncedAt: Instant?

    /**
     * Indicates whether there has been at least one full sync, if any.
     *
     * Is null when unknown, for example when state is still being loaded from the database.
     */
    public val hasSynced: Boolean?

    /**
     * Error during uploading.
     *
     * Cleared on the next successful upload.
     */
    public val uploadError: Any?

    /**
     * Error during downloading (including connecting).
     *
     * Cleared on the next successful data download.
     */
    public val downloadError: Any?

    /**
     * Convenience getter for either the value of downloadError or uploadError
     */
    public val anyError: Any?

    /**
     * Available [PriorityStatusEntry] reporting the sync status for buckets within priorities.
     *
     * When buckets with different priorities are defined, this may contain entries before [hasSynced]
     * and [lastSyncedAt] are set to indicate that a partial (but no complete) sync has completed.
     * A completed [PriorityStatusEntry] at one priority level always includes all higher priorities too.
     */
    public val priorityStatusEntries: List<PriorityStatusEntry>

    /**
     * Status information for whether buckets in [priority] have been synchronized.
     */
    public fun statusForPriority(priority: BucketPriority): PriorityStatusEntry {
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
) : SyncStatusData {
    override val anyError
        get() = downloadError ?: uploadError

    internal fun applyCoreChanges(status: CoreSyncStatus): SyncStatusDataContainer {
        val completeSync = status.priorityStatus.firstOrNull { it.priority == BucketPriority.FULL_SYNC_PRIORITY }

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
) : SyncStatusData {
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

    override fun toString(): String =
        "SyncStatus(connected=$connected, connecting=$connecting, downloading=$downloading, uploading=$uploading, lastSyncedAt=$lastSyncedAt, hasSynced=$hasSynced, error=$anyError)"

    public companion object {
        public fun empty(): SyncStatus = SyncStatus()
    }
}
