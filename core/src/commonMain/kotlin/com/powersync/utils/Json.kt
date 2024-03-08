package com.powersync.utils

import kotlinx.serialization.json.Json

/**
 * A global instance of a JSON serializer.
 */
object JsonUtil {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

