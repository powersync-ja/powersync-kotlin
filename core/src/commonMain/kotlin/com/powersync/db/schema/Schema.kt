package com.powersync.db.schema

import kotlinx.serialization.Serializable

@Serializable
public data class Schema(
    val tables: List<Table>,
) {
    init {
        validate()
    }

    public constructor(vararg tables: Table) : this(tables.asList())

    public fun validate() {
        val tableNames = mutableSetOf<String>()
        tables.forEach { table ->
            if (!tableNames.add(table.name)) {
                throw AssertionError("Duplicate table name: ${table.name}")
            }
            table.validate()
        }
    }
}
