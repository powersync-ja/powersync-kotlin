package co.powersync.db.schema

import kotlinx.serialization.Serializable

/** A single column in a table schema. */
@Serializable
data class Column(
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
    companion object {
        /** Create a TEXT column. */
        fun text(name: String) = Column(name, ColumnType.TEXT)

        /** Create an INTEGER column. */
        fun integer(name: String) = Column(name, ColumnType.INTEGER)

        /** Create a REAL column. */
        fun real(name: String) = Column(name, ColumnType.REAL)
    }
}