package com.powersync.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.powersync.connectors.PowerSyncBackendConnector
import kotlinx.datetime.Instant

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
}

internal data class SyncStatusDataContainer(
    override val connected: Boolean = false,
    override val connecting: Boolean = false,
    override val downloading: Boolean = false,
    override val uploading: Boolean = false,
    override val lastSyncedAt: Instant? = null,
    override val hasSynced: Boolean? = null,
    override val uploadError: Any? = null,
    override val downloadError: Any? = null,
) : SyncStatusData {
    override val anyError
        get() = downloadError ?: uploadError
}


public data class SyncStatus internal constructor(
    private var data: SyncStatusDataContainer = SyncStatusDataContainer()
) : SyncStatusData {
    private val stateFlow: MutableStateFlow<SyncStatusDataContainer> = MutableStateFlow(data)

    /**
     * @returns a flow which emits whenever the sync status has changed
     */
    public fun asFlow(): SharedFlow<SyncStatusData> {
        return stateFlow.asSharedFlow()
    }

    /**
     * Updates the internal sync status indicators and emits Flow updates
     */
    internal fun update(
        connected: Boolean? = null,
        connecting: Boolean? = null,
        downloading: Boolean? = null,
        uploading: Boolean? = null,
        hasSynced: Boolean? = null,
        lastSyncedAt: Instant? = null,
        uploadError: Any? = null,
        downloadError: Any? = null,
        clearUploadError: Boolean = false,
        clearDownloadError: Boolean = false,
    ) {
        data = data.copy(
            connected = connected ?: data.connected,
            connecting = connecting ?: data.connecting,
            downloading = downloading ?: data.downloading,
            uploading = uploading ?: data.uploading,
            lastSyncedAt = lastSyncedAt ?: data.lastSyncedAt,
            hasSynced = hasSynced ?: data.hasSynced,
            uploadError = if (clearUploadError) null else uploadError,
            downloadError = if (clearDownloadError) null else downloadError,
        )
        stateFlow.value = data
    }

    override val anyError: Any?
        get() = data.anyError

    override val connected: Boolean
        get() = data.connected

    override val connecting: Boolean
        get() = data.connecting

    override val downloading: Boolean
        get() = data.downloading

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

    override fun toString(): String {
        return "SyncStatus(connected=$connected, connecting=$connecting, downloading=$downloading, uploading=$uploading, lastSyncedAt=$lastSyncedAt, hasSynced=$hasSynced, error=$anyError)"
    }

    public companion object {
        public fun empty(): SyncStatus = SyncStatus()
    }
}
