package com.powersync.sync

import com.powersync.bucket.OplogEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SyncDataBucket (
    val bucket: String,
    val data: List<OplogEntry>,
    @SerialName("has_more") val hasMore: Boolean = false,
    val after: String?,
    @SerialName("next_after")val nextAfter: String?
)