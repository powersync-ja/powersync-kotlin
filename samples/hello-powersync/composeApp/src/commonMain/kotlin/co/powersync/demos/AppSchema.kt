package co.powersync.demos

import co.powersync.db.schema.Column
import co.powersync.db.schema.Schema
import co.powersync.db.schema.Table

val AppSchema: Schema = Schema(
    listOf(
        Table(
            "users",
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