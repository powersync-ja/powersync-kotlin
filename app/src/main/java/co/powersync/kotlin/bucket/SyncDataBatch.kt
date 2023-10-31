package co.powersync.kotlin.bucket

import kotlinx.serialization.Serializable

@Serializable
data class SyncDataBatch(val buckets: Array<SyncDataBucket>)