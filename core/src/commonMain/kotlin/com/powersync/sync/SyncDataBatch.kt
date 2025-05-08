package com.powersync.sync

import kotlinx.serialization.Serializable

@LegacySyncImplementation
@Serializable
internal data class SyncDataBatch(
    val buckets: List<SyncLine.SyncDataBucket>,
)
