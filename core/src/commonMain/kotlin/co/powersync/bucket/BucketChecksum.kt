package co.powersync.bucket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BucketChecksum(
    val bucket: String,
    val checksum: Int,
    val count: Int? = null,
    @SerialName("last_op_id") val lastOpId: String? = null
)