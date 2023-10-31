package co.powersync.kotlin.bucket

import kotlinx.serialization.Serializable

@Serializable
data class SyncDataBucket (
    val bucket: String,
    val data: Array<OplogEntry>,
    /**
     * True if the response does not contain all the data for this bucket, and another request must be made.
     */
    val has_more: Boolean,
    /**
     * The `after` specified in the request.
     */
    val after: String,
    /**
     * Use this for the next request.
     */
    val next_after: String
)