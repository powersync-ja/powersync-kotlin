package com.powersync.db.schema

/**
 * Extension functions for working with schemas and unique constraints.
 */

/**
 * Find a table by name in the schema.
 */
public fun Schema.getTable(name: String): Table? = tables.find { it.name == name }

/**
 * Get all tables that have at least one unique constraint.
 */
public fun Schema.getTablesWithUniqueConstraints(): List<Table> = 
    tables.filter { table -> table.indexes.any { it.unique } }

/**
 * Check if a table has any unique constraints.
 */
public fun Table.hasUniqueConstraints(): Boolean = indexes.any { it.unique }

/**
 * Get all unique indexes for a table.
 */
public fun Table.getUniqueIndexes(): List<Index> = indexes.filter { it.unique }

/**
 * Create a table builder with a unique constraint.
 * 
 * Example:
 * ```
 * val userTable = Table(
 *     name = "users",
 *     columns = listOf(
 *         Column.text("email"),
 *         Column.text("username"),
 *         Column.text("name")
 *     ),
 *     indexes = listOf(
 *         Index.unique("idx_email", "email"),
 *         Index.unique("idx_username", "username")
 *     )
 * )
 * ```
 */
public fun Table.Companion.withUnique(
    name: String,
    columns: List<Column>,
    uniqueColumns: List<String>,
    additionalIndexes: List<Index> = emptyList()
): Table {
    val uniqueIndex = Index.unique("${name}_unique", uniqueColumns)
    return Table(
        name = name,
        columns = columns,
        indexes = listOf(uniqueIndex) + additionalIndexes
    )
}

/**
 * Check if a column participates in any unique constraint.
 */
public fun Table.isColumnUnique(columnName: String): Boolean =
    indexes.any { index ->
        index.unique && index.columns.any { it.column == columnName }
    }