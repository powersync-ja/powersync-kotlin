package com.powersync.bucket

import kotlinx.serialization.Serializable

@Serializable
internal data class BucketRequest(val name: String, val after: String)