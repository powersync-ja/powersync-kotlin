package co.powersync.bucket

import co.powersync.bucket.BucketChecksum
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ChecksumCache(
    @JsonNames("last_op_id") val lostOpId: String,
    val checksums: Map<String, BucketChecksum>
) {
}