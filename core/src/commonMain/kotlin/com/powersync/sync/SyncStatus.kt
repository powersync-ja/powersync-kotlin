package com.powersync.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Instant

interface SyncStatusData {
    /**
     * true if currently connected.
     *
     * This means the PowerSync connection is ready to download, and [PowerSyncBackendConnector.uploadData] may be called for any local changes.
     */
    val connected: Boolean

    /**
     * true if the PowerSync connection is busy connecting.
     *
     * During this stage, [PowerSyncBackendConnector.uploadData] may already be called, and [uploading] may be true.
     */
    val connecting: Boolean

    /**
     * true if actively downloading changes.
     *
     * This is only true when [connected] is also true.
     */
    val downloading: Boolean

    /**
     * true if uploading changes
     */
    val uploading: Boolean

    /**
     * Time that a last sync has fully completed, if any.
     *
     * Currently this is reset to null after a restart.
     */
    val lastSyncedAt: Instant?

    /**
     * Error during uploading.
     *
     * Cleared on the next successful upload.
     */
    val uploadError: Any?

    /**
     * Error during downloading (including connecting).
     *
     * Cleared on the next successful data download.
     */
    val downloadError: Any?

    /**
     * Convenience getter for either the value of downloadError or uploadError
     */
    val anyError: Any?
}

data class SyncStatusDataContainer(
    override val connected: Boolean = false,
    override val connecting: Boolean = false,
    override val downloading: Boolean = false,
    override val uploading: Boolean = false,
    override val lastSyncedAt: Instant? = null,
    override val uploadError: Any? = null,
    override val downloadError: Any? = null,
) : SyncStatusData {
    override val anyError
        get() = downloadError ?: uploadError
}


data class SyncStatus(
    var data: SyncStatusData = SyncStatusDataContainer()
) : SyncStatusData {
    private val stateFlow: MutableStateFlow<SyncStatusData> = MutableStateFlow(data)

    /**
     * @returns a flow which emits whenever the sync status has changed
     */
    fun asFlow(): SharedFlow<SyncStatusData> {
        return stateFlow.asSharedFlow()
    }

    /**
     * Updates the internal sync status indicators and emits Flow updates
     */
    internal fun update(
        connected: Boolean = data.connected,
        connecting: Boolean = data.connecting,
        downloading: Boolean = data.downloading,
        uploading: Boolean = data.uploading,
        lastSyncedAt: Instant? = data.lastSyncedAt,
        uploadError: Any? = data.uploadError,
        downloadError: Any? = data.downloadError,
        clearUploadError: Boolean? = false,
        clearDownloadError: Boolean? = false,
    ) {
        data = SyncStatusDataContainer(
            connected = connected,
            connecting = connecting,
            downloading = downloading,
            uploading = uploading,
            lastSyncedAt = lastSyncedAt,
            uploadError = if (clearUploadError == true) null else uploadError,
            downloadError = if (clearDownloadError == true) null else downloadError,
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

    override val uploadError: Any?
        get() = data.uploadError

    override val downloadError: Any?
        get() = data.downloadError

    override fun toString(): String {
        return "SyncStatus(connected=$connected, connecting=$connecting, downloading=$downloading, uploading=$uploading, lastSyncedAt=$lastSyncedAt, error=$anyError)"
    }
}