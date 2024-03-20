package com.powersync.demos

import com.powersync.db.schema.Column
import com.powersync.db.schema.Index
import com.powersync.db.schema.IndexedColumn
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

val AppSchema: Schema = Schema(
    listOf(
        Table(
            name = "customers",
            columns = listOf(
                Column.text("name"),
                Column.text("email")
            ),
            indexes = listOf(
                Index("name", listOf(IndexedColumn.descending("name")))
            )
        )
    )
)

data class User(
    val id: String,
    val name: String,
    val email: String
)