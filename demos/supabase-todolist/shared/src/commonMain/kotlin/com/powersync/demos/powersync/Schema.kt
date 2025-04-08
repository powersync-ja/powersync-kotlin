package com.powersync.demos.powersync

import com.powersync.db.SqlCursor
import com.powersync.db.getBoolean
import com.powersync.db.getStringOptional
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
) {
    companion object {
        /**
         * Creates a ListItem instance from a database row represented as a SqlCursor.
         * Handles necessary type casting. Assumes non-null fields based on schema.
         *
         * @param row A SqlCursor representing a row, typically from PowerSync's getAll.
         * @return A ListItem instance.
         * @throws ClassCastException if expected fields are missing or have wrong types.
         */
        fun fromRow(cursor: SqlCursor): ListItem {
            return ListItem(
                id = cursor.getStringOptional("id") as String,
                name = cursor.getStringOptional("name") as String,
                createdAt = cursor.getStringOptional("created_at") as String,
                ownerId = cursor.getStringOptional("owner_id") as String
            )
        }
    }
}

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
) {
    companion object {
        /**
         * Creates a TodoItem instance from a database row represented as a SqlCursor.
         * Handles necessary type casting. Assumes non-null fields based on schema.
         *
         * @param row A SqlCursor representing a row, typically from PowerSync's getAll.
         * @return A TodoItem instance.
         * @throws ClassCastException if expected fields are missing or have wrong types.
         */
        fun fromRow(cursor: SqlCursor): TodoItem {
            return TodoItem(
                id = cursor.getStringOptional("id") as String,
                listId = cursor.getStringOptional("list_id") as String,
                description = cursor.getStringOptional("description") as String,
                completed = cursor.getBoolean("completed"),
                photoId = cursor.getStringOptional("photo_id"),
                createdAt = cursor.getStringOptional("created_at"),
                completedAt = cursor.getStringOptional("completed_at"),
                createdBy = cursor.getStringOptional("created_by"),
                completedBy = cursor.getStringOptional("completed_by")
            )
        }
    }
}


