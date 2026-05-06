package com.powersync.db.schema

import kotlinx.serialization.Serializable

/** A single column in a table schema. */
@Serializable
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
