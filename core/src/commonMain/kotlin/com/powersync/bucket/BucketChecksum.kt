package com.powersync.bucket

import com.powersync.sync.LegacySyncImplementation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@LegacySyncImplementation
@Serializable
internal data class BucketChecksum(
    val bucket: String,
    val priority: StreamPriority = StreamPriority.DEFAULT_PRIORITY,
    val checksum: Int,
    val count: Int? = null,
    @SerialName("last_op_id") val lastOpId: String? = null,
    val subscriptions: JsonArray? = null,
)
