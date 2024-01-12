package co.powersync.bucket

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class BucketState(
    val bucket: String,
    @JsonNames("op_id") val opId: String
) {
    override fun toString() = "BucketState<$bucket:$opId>"
}

