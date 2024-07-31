package com.powersync.demos.powersync

import com.powersync.db.schema.Column
import com.powersync.db.schema.Index
import com.powersync.db.schema.IndexedColumn
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table

const val LISTS_TABLE = "lists"
const val TODOS_TABLE = "todos"

val todos = Table(
    TODOS_TABLE,
    listOf(
        Column.text("created_at"),
        Column.text("completed_at"),
        Column.text("description"),
        Column.text("created_by"),
        Column.text("completed_by"),
        // 0 or 1 to represent false or true
        Column.integer("completed"),
        Column.text("list_id"),
        Column.text("photo_id")
    ),
    indexes = listOf(
        Index(
            name = "listid",
            columns = listOf(IndexedColumn("list_id"))
        )
    )
)

val lists = Table(
    LISTS_TABLE,
    listOf(
        Column.text("created_at"),
        Column.text("name"),
        Column.text("owner_id")
    )
)

val schema: Schema = Schema(
    listOf(
        todos,
        lists,
    )
)

data class ListItem(
    val id: String,
    val name: String,
    val createdAt: String,
    val ownerId: String
)

data class TodoItem(
    val id: String,
    val listId: String,
    val photoId: String?,
    val createdAt: String?,
    val completedAt: String?,
    val description: String,
    val createdBy: String?,
    val completedBy: String?,
    val completed: Boolean = false
)
