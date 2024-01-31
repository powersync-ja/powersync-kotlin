package com.powersync.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncDataBatch(val buckets: List<SyncDataBucket>)