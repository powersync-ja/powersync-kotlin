package com.powersync.bucket

import com.powersync.sync.LegacySyncImplementation
import kotlinx.serialization.Serializable

@LegacySyncImplementation
@Serializable
internal data class BucketRequest(
    val name: String,
    val after: String,
)
