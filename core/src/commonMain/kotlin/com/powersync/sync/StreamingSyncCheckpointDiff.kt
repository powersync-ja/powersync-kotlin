package com.powersync.sync

import com.powersync.bucket.BucketChecksum
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class StreamingSyncCheckpointDiff(
    @SerialName("last_op_id") val lastOpId: String,
    @SerialName("updated_buckets") val updatedBuckets: List<BucketChecksum>,
    @SerialName("removed_buckets") val removedBuckets: List<String>,
    @SerialName("write_checkpoint") val writeCheckpoint: String? = null,
)
