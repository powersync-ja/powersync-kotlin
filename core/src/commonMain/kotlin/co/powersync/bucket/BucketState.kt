package co.powersync.bucket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BucketState(
    val bucket: String,
    @SerialName("op_id") val opId: String
) {
    override fun toString() = "BucketState<$bucket:$opId>"
}

