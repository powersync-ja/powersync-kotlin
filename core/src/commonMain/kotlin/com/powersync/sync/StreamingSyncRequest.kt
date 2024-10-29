package com.powersync.sync

import com.powersync.bucket.BucketRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class StreamingSyncRequest(
    val buckets: List<BucketRequest>,
    @SerialName("include_checksum") val includeChecksum: Boolean = true,
    @SerialName("client_id") val clientId: String,
    val parameters: JsonObject = JsonObject(mapOf()),
) {
    @SerialName("raw_data")
    private val rawData: Boolean = true
}
