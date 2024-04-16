package com.powersync.db.schema

import kotlinx.serialization.Serializable

/** A single column in a table schema. */
@Serializable
public data class Column(
    /** Name of the column. */
    val name: String,

    /** Type of the column.
     *
     * If the underlying data does not match this type,
     * it is cast automatically.
     *
     * For details on the cast, see:
     * https://www.sqlite.org/lang_expr.html#castexpr
     */
    val type: ColumnType
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