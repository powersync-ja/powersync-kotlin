package co.powersync.sync

import co.powersync.bucket.BucketChecksum
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class StreamingSyncCheckpointDiff(
    @JsonNames("last_op_id") val lastOpId: String,
    @JsonNames( "updated_buckets") val updatedBuckets: List<BucketChecksum>,
    @JsonNames("removed_buckets") val removedBuckets: List<String>,
    @JsonNames("write_checkpoint") val writeCheckpoint: String
)