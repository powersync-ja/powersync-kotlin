package co.powersync.bucket

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Checkpoint(
    @JsonNames("last_op_id") val lastOpId: String,
    @JsonNames("buckets") val checksums: List<BucketChecksum>,
    @JsonNames("write_checkpoint") val writeCheckpoint: String? = null
) {

    fun clone(): Checkpoint {
        return Checkpoint(lastOpId, checksums, writeCheckpoint)
    }
}