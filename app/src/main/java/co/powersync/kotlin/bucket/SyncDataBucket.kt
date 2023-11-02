package co.powersync.kotlin.bucket

import kotlinx.serialization.Serializable

@Serializable
data class SyncDataBucketJSON(
    val bucket: String,
    val has_more: Boolean, // TODO is this optional?
    val after: String, // TODO is this optional?
    val next_after: String,// TODO is this optional?
    val data: Array<OplogEntryJSON>
)

@Serializable
data class SyncDataBucket(

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


) {
    companion object {
        fun fromRow(row: SyncDataBucketJSON): SyncDataBucket {
            return SyncDataBucket(
                bucket = row.bucket,
                data = row.data.map { i -> OplogEntry.fromRow(i) }.toTypedArray(),
                after = row.after,
                has_more = row.has_more,
                next_after = row.next_after
            )
        }
    }
}