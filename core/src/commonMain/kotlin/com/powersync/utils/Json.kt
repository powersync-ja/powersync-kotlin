package com.powersync.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A global instance of a JSON serializer.
 */
internal object JsonUtil {
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

internal fun convertMapToJson(map: Map<String, Any>?): JsonObject {
    if (map == null) return JsonObject(emptyMap())

    val result = map.mapValues { (_, value) ->
        when (value) {
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            else -> JsonNull
        }
    }
    return JsonObject(result)
}
