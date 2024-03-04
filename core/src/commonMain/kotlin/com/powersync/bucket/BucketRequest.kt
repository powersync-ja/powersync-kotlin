package com.powersync.bucket

import kotlinx.serialization.Serializable

@Serializable
data class BucketRequest(val name: String, val after: String)