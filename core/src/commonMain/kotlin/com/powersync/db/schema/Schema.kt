package com.powersync.db.schema

import kotlinx.serialization.Serializable

@Serializable
public data class Schema(val tables: List<Table>) {
    public constructor(vararg tables: Table) : this(tables.asList())
}