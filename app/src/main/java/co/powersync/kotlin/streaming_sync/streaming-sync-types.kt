package co.powersync.kotlin.streaming_sync

class BucketRequest (
    val name: String,

    /**
     * Base-10 number. Sync all data from this bucket with op_id > after.
     */
    val after: String
)

class StreamingSyncRequest (
    /**
     * Existing bucket states.
     */
    val buckets: Array<BucketRequest>?,

    /**
     * If specified, limit the response to only include these buckets.
     */
    val only: Array<String>?,

    /**
     * Whether or not to compute a checksum for each checkpoint
     */
    val include_checksum: Boolean
)