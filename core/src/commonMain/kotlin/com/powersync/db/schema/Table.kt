package com.powersync.db.schema

import kotlinx.serialization.Serializable

private const val MAX_AMOUNT_OF_COLUMNS = 63

/**
 * A single table in the schema.
 */
@Serializable
public data class Table constructor(
    /**
     * The synced table name, matching sync rules.
     */
    var name: String,
    /**
     * List of columns.
     */
    var columns: List<Column>,
    /**
     * List of indexes.
     */
    var indexes: List<Index> = listOf(),
    /**
     * Whether the table only exists only.
     */
    private val localOnly: Boolean = false,
    /**
     * Whether this is an insert-only table.
     */
    private val insertOnly: Boolean = false,
    /**
     * Override the name for the view
     */
    private val _viewNameOverride: String? = null
) {

    init {
        /**
         * Need to set the column definition for each index column.
         * This is required for serialization
         */
        indexes.forEach { index ->
            index.columns.forEach {
                val matchingColumn = columns.find { c -> c.name == it.column }
                    ?: throw AssertionError("Could not find column definition for index ${index.name}:${it.column}")
                it.setColumnDefinition(column = matchingColumn)
            }
        }
    }

    public companion object {
        /**
         * Create a table that only exists locally.
         *
         * This table does not record changes, and is not synchronized from the service.
         */
        public fun localOnly(
            name: String,
            columns: List<Column>,
            indexes: List<Index> = listOf(),
            viewName: String? = null
        ): Table {
            return Table(
                name,
                columns,
                indexes,
                localOnly = true,
                insertOnly = false,
                _viewNameOverride = viewName
            )
        }

        /**
         * Create a table that only supports inserts.
         *
         * This table records INSERT statements, but does not persist data locally.
         *
         * SELECT queries on the table will always return 0 rows.
         */
        public fun insertOnly(name: String, columns: List<Column>, viewName: String? = null): Table {
            return Table(
                name,
                columns,
                indexes = listOf(),
                localOnly = false,
                insertOnly = true,
                _viewNameOverride = viewName
            )
        }
    }

    /**
     * Internal use only.
     *
     * Name of the table that stores the underlying data.
     */
    internal val internalName: String
        get() = if (localOnly) "ps_data_local__$name" else "ps_data__$name"

    public operator fun get(columnName: String): Column {
        return columns.first { it.name == columnName }
    }

    /**
     * Whether this table name is valid.
     */
    val validName: Boolean
        get() = !invalidSqliteCharacters.containsMatchIn(name) &&
                (_viewNameOverride == null || !invalidSqliteCharacters.containsMatchIn(
                    _viewNameOverride
                ))


    /**
     * Check that there are no issues in the table definition.
     */
    public fun validate() {
        if (columns.size > MAX_AMOUNT_OF_COLUMNS) {
            throw AssertionError("Table $name has more than $MAX_AMOUNT_OF_COLUMNS columns, which is not supported")
        }

        if (invalidSqliteCharacters.containsMatchIn(name)) {
            throw AssertionError("Invalid characters in table name: $name")
        }

        if (_viewNameOverride != null && invalidSqliteCharacters.containsMatchIn(
                _viewNameOverride
            )
        ) {
            throw AssertionError("Invalid characters in view name: $_viewNameOverride")
        }

        val columnNames = mutableSetOf("id")
        for (column in columns) {
            when {
                column.name == "id" -> {
                    throw AssertionError("$name: id column is automatically added, custom id columns are not supported")
                }

                columnNames.contains(column.name) -> {
                    throw AssertionError("Duplicate column $name.${column.name}")
                }

                invalidSqliteCharacters.containsMatchIn(column.name) -> {
                    throw AssertionError("Invalid characters in column name: $name.${column.name}")
                }

                else -> columnNames.add(column.name)
            }
        }

        val indexNames = mutableSetOf<String>()
        for (index in indexes) {
            when {
                indexNames.contains(index.name) -> {
                    throw AssertionError("Duplicate index $name.${index.name}")
                }

                invalidSqliteCharacters.containsMatchIn(index.name) -> {
                    throw AssertionError("Invalid characters in index name: $name.${index.name}")
                }

                else -> {
                    for (column in index.columns) {
                        if (!columnNames.contains(column.column)) {
                            throw AssertionError("Column $name.${column.column} not found for index ${index.name}")
                        }
                    }
                    indexNames.add(index.name)
                }
            }
        }
    }

    /**
     * Name for the view, used for queries.
     * Defaults to the synced table name.
     */
    public val viewName: String
        get() = _viewNameOverride ?: name
}