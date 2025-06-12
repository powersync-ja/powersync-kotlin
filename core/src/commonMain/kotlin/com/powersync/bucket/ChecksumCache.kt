package com.powersync.bucket

import com.powersync.sync.LegacySyncImplementation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@LegacySyncImplementation
@Serializable
internal data class ChecksumCache(
    @SerialName("last_op_id") val lostOpId: String,
    val checksums: Map<String, BucketChecksum>,
)
