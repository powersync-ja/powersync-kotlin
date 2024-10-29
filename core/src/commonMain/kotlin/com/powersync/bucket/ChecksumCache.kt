package com.powersync.bucket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChecksumCache(
    @SerialName("last_op_id") val lostOpId: String,
    val checksums: Map<String, BucketChecksum>,
)
