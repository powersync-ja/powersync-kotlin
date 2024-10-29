package com.powersync.sync

import kotlinx.serialization.Serializable

@Serializable
internal data class SyncDataBatch(
    val buckets: List<SyncDataBucket>,
)
