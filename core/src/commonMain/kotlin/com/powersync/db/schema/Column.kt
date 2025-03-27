package com.powersync.db.schema

import kotlinx.serialization.Serializable

/** A single column in a table schema. */
public data class Column(
    val name: String,
    val type: ColumnType,
) {
    public companion object {
        /** Create a TEXT column. */
        public fun text(name: String): Column = Column(name, ColumnType.TEXT)

        /** Create an INTEGER column. */
        public fun integer(name: String): Column = Column(name, ColumnType.INTEGER)

        /** Create a REAL column. */
        public fun real(name: String): Column = Column(name, ColumnType.REAL)
    }
}

@Serializable
internal data class SerializableColumn(
    val name: String,
    val type: ColumnType,
)

internal fun Column.toSerializable(): SerializableColumn = with(this) { SerializableColumn(name, type) }
