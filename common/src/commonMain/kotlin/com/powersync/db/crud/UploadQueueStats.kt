package com.powersync.db.crud

/**
 * Stats of the local upload queue.
 */
public data class UploadQueueStats(
    /**
     * Number of records in the upload queue.
     */
    val count: Int,
    /**
     * Size of the upload queue in bytes.
     */
    val size: Int?,
) {
    override fun toString(): String =
        if (size == null) {
            "UploadQueueStats<count: $count>"
        } else {
            "UploadQueueStats<count: $count size: ${size / 1024}kB>"
        }
}
