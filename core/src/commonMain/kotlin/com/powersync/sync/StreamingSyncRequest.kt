package com.powersync.sync

import com.powersync.bucket.BucketRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StreamingSyncRequest(
    val buckets: List<BucketRequest>,
    @SerialName("include_checksum") val includeChecksum: Boolean = true,
) {
    @SerialName("raw_data") private val rawData: Boolean = true
}
