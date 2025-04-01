package com.powersync.attachments.testutils

import com.powersync.db.SqlCursor
import com.powersync.db.getString
import com.powersync.db.getStringOptional
import com.powersync.db.schema.Column
import com.powersync.db.schema.Table

data class UserRow(
    val id: String,
    val name: String,
    val email: String,
    val photoId: String?,
) {
    companion object {
        fun from(cursor: SqlCursor): UserRow =
            UserRow(
                id = cursor.getString("id"),
                name = cursor.getString("name"),
                email = cursor.getString("email"),
                photoId = cursor.getStringOptional("photo_id"),
            )

        val table =
            Table(
                name = "users",
                columns =
                    listOf(
                        Column.text("name"),
                        Column.text("email"),
                        Column.text("photo_id"),
                    ),
            )
    }
}
