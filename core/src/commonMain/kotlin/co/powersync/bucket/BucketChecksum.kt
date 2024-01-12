package co.powersync.bucket

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class BucketChecksum(
    val bucket: String,
    val checksum: Int,
    val count: Int? = null,
    @JsonNames("last_op_id") val lastOpId: String? = null
) {
}