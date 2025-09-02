package com.powersync.db.crud

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * A named collection of values as they appear in a SQLite row.
 *
 * We represent values as a `Map<String, String?>` to ensure compatible with earlier versions of the
 * SDK, but the [typed] getter can be used to obtain a `Map<String, Any>` where values are either
 * [String]s, [Int]s or [Double]s.
 */
@OptIn(ExperimentalObjCRefinement::class)
public interface SqliteRow : Map<String, String?> {
    /**
     * A typed view of the SQLite row.
     */
    public val typed: Map<String, Any?>

    /**
     * A [JsonObject] of all values in this row that can be represented as JSON.
     */
    @HiddenFromObjC
    public val jsonValues: JsonObject
}

/**
 * A [SqliteRow] implemented over a [JsonObject] view.
 */
internal class SerializedRow(
    override val jsonValues: JsonObject,
) : AbstractMap<String, String?>(),
    SqliteRow {
    override val entries: Set<Map.Entry<String, String?>> =
        jsonValues.entries.mapTo(
            mutableSetOf(),
            ::ToStringEntry,
        )

    override val typed: Map<String, Any?> = TypedRow(jsonValues)
}

private data class ToStringEntry(
    val inner: Map.Entry<String, JsonElement>,
) : Map.Entry<String, String?> {
    override val key: String
        get() = inner.key
    override val value: String?
        get() = inner.value.jsonPrimitive.contentOrNull
}

private class TypedRow(
    inner: JsonObject,
) : AbstractMap<String, Any?>() {
    override val entries: Set<Map.Entry<String, Any?>> =
        inner.entries.mapTo(
            mutableSetOf(),
            ::ToTypedEntry,
        )
}

private data class ToTypedEntry(
    val inner: Map.Entry<String, JsonElement>,
) : Map.Entry<String, Any?> {
    override val key: String
        get() = inner.key
    override val value: Any?
        get() = inner.value.jsonPrimitive.asData()

    companion object {
        private fun JsonPrimitive.asData(): Any? =
            if (this === JsonNull) {
                null
            } else if (isString) {
                content
            } else {
                content.jsonNumberOrBoolean()
            }

        private fun String.jsonNumberOrBoolean(): Any =
            when {
                this == "true" -> true
                this == "false" -> false
                this.any { char -> char == '.' || char == 'e' || char == 'E' } -> this.toDouble()
                else -> this.toInt()
            }
    }
}
