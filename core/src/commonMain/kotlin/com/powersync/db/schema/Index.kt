package com.powersync.db.schema

import kotlinx.serialization.Serializable

public data class Index(
    /**
     * Descriptive name of the index.
     */
    val name: String,
    /**
     * List of columns used for the index.
     */
    val columns: List<IndexedColumn>,
    /**
     * Whether this index enforces a unique constraint.
     */
    val unique: Boolean = false,
) {
    /**
     * @param name Descriptive name of the index.
     * @param columns List of columns used for the index.
     */
    public constructor(name: String, vararg columns: IndexedColumn) : this(name, columns.asList(), false)

    /**
     * Construct a new index with the specified column names.
     */
    public companion object {
        public fun ascending(
            name: String,
            columns: List<String>,
        ): Index = Index(name, columns.map { IndexedColumn.ascending(it) }, unique = false)
        
        /**
         * Create a unique index with the specified column names.
         */
        public fun unique(
            name: String,
            columns: List<String>,
        ): Index = Index(name, columns.map { IndexedColumn.ascending(it) }, unique = true)
        
        /**
         * Create a unique index with a single column.
         */
        public fun unique(
            name: String,
            column: String,
        ): Index = unique(name, listOf(column))
    }

    /**
     * Internal use only.
     * Specifies the full name of this index on a table.
     */
    internal fun fullName(table: Table): String = "${table.internalName}__$name"

    /**
     * Internal use only.
     * Returns a SQL statement that creates this index.
     */
    internal fun toSqlDefinition(table: Table): String {
        val fields = columns.joinToString(", ") { it.toSql(table) }
        val indexType = if (unique) "UNIQUE INDEX" else "INDEX"
        return """CREATE $indexType "${fullName(table)}" ON "${table.internalName}"($fields)"""
    }
}

@Serializable
internal data class SerializableIndex(
    val name: String,
    val columns: List<SerializableIndexColumn>,
    val unique: Boolean = false,
)

internal fun Index.toSerializable(): SerializableIndex =
    with(this) {
        SerializableIndex(
            name,
            columns.map { it.toSerializable() },
            unique,
        )
    }
