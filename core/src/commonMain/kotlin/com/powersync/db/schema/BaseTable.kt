package com.powersync.db.schema

public sealed interface BaseTable {
    /**
     * The name of the table.
     */
    public val name: String

    /**
     * Check that there are no issues in the table definition.
     */
    public fun validate()
}
