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

public sealed class JsonParam {
    public data class Number(val value: kotlin.Number) : JsonParam()
    public data class String(val value: kotlin.String) : JsonParam()
    public data class Boolean(val value: kotlin.Boolean) : JsonParam()
    public data class Map(val value: kotlin.collections.Map<kotlin.String, JsonParam>) : JsonParam()
    public data class Collection(val value: kotlin.collections.Collection<JsonParam>) : JsonParam()
    public data class JsonElement(val value: kotlinx.serialization.json.JsonElement) : JsonParam()
    public data object Null : JsonParam()

    internal fun toJsonElement(): kotlinx.serialization.json.JsonElement = when (this) {
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map -> JsonObject(value.mapValues { it.value.toJsonElement() })
        is Collection -> JsonArray(value.map { it.toJsonElement() })
        is JsonElement -> value
        Null -> JsonNull
    }
}

public fun Any?.toJsonParam(): JsonParam = when (this) {
    is Number -> JsonParam.Number(this)
    is String -> JsonParam.String(this)
    is Boolean -> JsonParam.Boolean(this)
    is Map<*, *> -> JsonParam.Map(this.mapKeys { it.key.toString() }
        .mapValues { it.value.toJsonParam() })
    is List<*> -> JsonParam.Collection(this.map { it.toJsonParam() })
    is Array<*> -> JsonParam.Collection(this.map { it.toJsonParam() })
    is JsonElement -> JsonParam.JsonElement(this)
    null -> JsonParam.Null
    else -> throw IllegalArgumentException("Unsupported type for JsonParam: $this")
}

public fun Map<String, JsonParam?>.toJsonObject(): JsonObject {
    return JsonObject(this.mapValues { (_, value) ->
        value?.toJsonElement() ?: JsonNull
    })
}