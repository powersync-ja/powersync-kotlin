package com.powersync.db.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Describes an indexed column.
 */
public data class IndexedColumn(
    val column: String,
    val ascending: Boolean = true,
    private var columnDefinition: Column? = null,
    var type: ColumnType? = null,
) {
    public companion object {
        public fun ascending(column: String): IndexedColumn = IndexedColumn(column, true)

        public fun descending(column: String): IndexedColumn = IndexedColumn(column, false)
    }

    /**
     * Sets the parent column definition. The column definition's type
     * is required for the serialized JSON payload of powersync_replace_schema
     */
    internal fun setColumnDefinition(column: Column) {
        this.type = column.type
        this.columnDefinition = column
    }

    internal fun toSql(table: Table): String {
        val fullColumn = table[column] // errors if not found
        return fullColumn.let {
            if (ascending) mapColumn(it) else "${mapColumn(it)} DESC"
        }
    }
}

@Serializable
internal data class SerializableIndexColumn(
    @SerialName("name")
    val column: String,
    val type: ColumnType?,
    val ascending: Boolean,
)

internal fun IndexedColumn.toSerializable(): SerializableIndexColumn =
    with(this) {
        SerializableIndexColumn(column, type, ascending)
    }

internal fun mapColumn(column: Column): String = "CAST(json_extract(data, ${column.name}) as ${column.type})"
