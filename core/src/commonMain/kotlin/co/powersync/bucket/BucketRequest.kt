package co.powersync.bucket

import kotlinx.serialization.Serializable

@Serializable
data class BucketRequest(val name: String, val after: String)