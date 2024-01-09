package co.powersync.db

import kotlinx.datetime.Instant

data class SyncStatus(
    /**
     * true if currently connected.
     *
     * This means the PowerSync connection is ready to download, and [PowerSyncBackendConnector.uploadData] may be called for any local changes.
     */
    var connected: Boolean = false,
    /**
     * true if the PowerSync connection is busy connecting.
     *
     * During this stage, [PowerSyncBackendConnector.uploadData] may already be called, and [uploading] may be true.
     */
    var connecting: Boolean = false,
    /**
     * true if actively downloading changes.
     *
     * This is only true when [connected] is also true.
     */
    var downloading: Boolean = false,
    /**
     * true if uploading changes
     */
    var uploading: Boolean = false,
    /**
     * Time that a last sync has fully completed, if any.
     *
     * Currently this is reset to null after a restart.
     */
    var lastSyncedAt: Instant? = null,
    /**
     * Error during uploading.
     *
     * Cleared on the next successful upload.
     */
    var uploadError: Any? = null,
    /**
     * Error during downloading (including connecting).
     *
     * Cleared on the next successful data download.
     */
    var downloadError: Any? = null
) {

    /**
     * Get the current [downloadError] or [uploadError].
     */
    val anyError: Any?
        get() = downloadError ?: uploadError

    override fun toString(): String {
        return "SyncStatus(connected=$connected, connecting=$connecting, downloading=$downloading, uploading=$uploading, lastSyncedAt=$lastSyncedAt, error=$anyError)"
    }
}