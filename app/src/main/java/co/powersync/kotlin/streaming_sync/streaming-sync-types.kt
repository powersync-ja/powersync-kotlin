package co.powersync.kotlin.streaming_sync

import co.powersync.kotlin.bucket.BucketChecksum
import co.powersync.kotlin.bucket.SyncDataBucketJSON
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

data class StreamingSyncDataJSON(
    val data: SyncDataBucketJSON
)

data class StreamingSyncCheckpointDiff(
    val last_op_id: String,
    val updated_buckets: Array<BucketChecksum>,
    val removed_buckets: Array<String>,
    val write_checkpoint: String
)

@Serializable
data class BucketRequest(
    val name: String,

    /**
     * Base-10 number. Sync all data from this bucket with op_id > after.
     */
    val after: String
)

@Serializable
data class StreamingSyncRequest(
    /**
     * Existing bucket states.
     */
    val buckets: Array<BucketRequest>?,

    /**
     * If specified, limit the response to only include these buckets.
     */
    val only: Array<String>? = null,

    /**
     * Whether or not to compute a checksum for each checkpoint
     */
    val include_checksum: Boolean
)


fun isStreamingSyncData(obj: JsonObject): Boolean {
    return obj.containsKey("data")
}

fun isStreamingKeepalive(obj: JsonObject): Boolean {
    return obj.containsKey("token_expires_in")
}

fun isStreamingSyncCheckpoint(obj: JsonObject): Boolean {
    return obj.containsKey("checkpoint")
}

fun isStreamingSyncCheckpointComplete(obj: JsonObject): Boolean {
    return obj.containsKey("checkpoint_complete")
}

fun isStreamingSyncCheckpointDiff(obj: JsonObject): Boolean {
    return obj.containsKey("checkpoint_diff")
}