package com.powersync.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

internal fun convertMapToJson(map: Map<String, Any?>?): JsonObject {
    if (map == null) return JsonObject(emptyMap())

    val result = map.mapValues { (_, value) ->
        convertToJsonElement(value)
    }
    return JsonObject(result)
}


internal fun convertToJsonElement(value: Any?): JsonElement {
    return when (value) {
        null -> JsonNull
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> convertMapToJson(value as Map<String, Any?>)
        is List<*> -> convertListToJsonArray(value)
        is Array<*> -> convertListToJsonArray(value.toList())
        else -> JsonNull
    }
}

internal fun convertListToJsonArray(list: List<Any?>): JsonArray {
    return JsonArray(list.map { convertToJsonElement(it) })
}