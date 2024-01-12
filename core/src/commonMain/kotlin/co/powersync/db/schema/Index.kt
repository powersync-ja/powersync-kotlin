package co.powersync.db.schema

import kotlinx.serialization.Serializable

@Serializable
data class Index(
    /**
     * Descriptive name of the index.
     */
    val name: String,

    /**
     * List of columns used for the index.
     */
    val columns: List<IndexedColumn>
) {

    /**
     * Construct a new index with the specified column names.
     */
    companion object {
        fun ascending(name: String, columns: List<String>): Index {
            return Index(name, columns.map { IndexedColumn.ascending(it) })
        }
    }

    /**
     * Internal use only.
     * Specifies the full name of this index on a table.
     */
    fun fullName(table: Table): String {
        return "${table.internalName}__$name"
    }

    /**
     * Internal use only.
     * Returns a SQL statement that creates this index.
     */
    fun toSqlDefinition(table: Table): String {
        val fields = columns.joinToString(", ") { it.toSql(table) }
        return """CREATE INDEX "${fullName(table)}" ON "${table.internalName}"($fields)"""
    }
}