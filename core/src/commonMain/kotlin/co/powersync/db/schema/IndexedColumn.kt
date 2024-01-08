package co.powersync.db.schema

/**
 * Describes an indexed column.
 */
open class IndexedColumn (
    /**
     * Name of the column to index.
     */
    val column: String,

    /**
     * Whether this column is stored in ascending order in the index.
     */
    private val ascending: Boolean = true
) {
    companion object {
        fun ascending(column: String) = IndexedColumn(column, true)
        fun descending(column: String) = IndexedColumn(column, false)
    }

    fun toSql(table: Table): String {
        val fullColumn = table[column] // errors if not found
        return fullColumn.let {
            if (ascending) mapColumn(it) else "${mapColumn(it)} DESC"
        }
    }
}

fun mapColumn(column: Column): String {
    return "CAST(json_extract(data, ${column.name}) as ${column.type})"
}