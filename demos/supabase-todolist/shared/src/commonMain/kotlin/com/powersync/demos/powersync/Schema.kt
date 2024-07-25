package com.powersync.demos.powersync

import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

val schema: Schema = Schema(
    listOf(
        Table(
            "todos",
            listOf(
                Column.text("description"),
                Column.integer("completed") // 0 or 1 to represent false or true
            )
        )
    )
)

data class TodoItem(
    val id: String,
    val description: String,
    val completed: Boolean = false
)
