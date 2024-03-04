package com.powersync.demos

import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

val AppSchema: Schema = Schema(
    listOf(
        Table(
            "customers",
            listOf(
                Column.text("name"),
                Column.text("email")
            )
        )
    )
)

data class User(
    val id: String,
    val name: String,
    val email: String
)