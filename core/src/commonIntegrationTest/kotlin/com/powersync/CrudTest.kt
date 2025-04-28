package com.powersync

import com.powersync.db.schema.Column
import com.powersync.db.schema.IncludeOldOptions
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import com.powersync.testutils.databaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CrudTest {
    @Test
    fun includeMetadata() =
        databaseTest {
            database.updateSchema(Schema(Table("lists", listOf(Column.text("name")), includeMetadata = true)))

            database.execute("INSERT INTO lists (id, name, _metadata) VALUES (uuid(), ?, ?)", listOf("entry", "so meta"))
            val batch = database.getNextCrudTransaction()
            batch!!.crud[0].metadata shouldBe "so meta"
        }

    @Test
    fun includeOldValues() =
        databaseTest {
            database.updateSchema(
                Schema(Table("lists", listOf(Column.text("name"), Column.text("content")), includeOld = IncludeOldOptions())),
            )

            database.execute("INSERT INTO lists (id, name, content) VALUES (uuid(), ?, ?)", listOf("entry", "content"))
            database.execute("DELETE FROM ps_crud")
            database.execute("UPDATE lists SET name = ?", listOf("new name"))

            val batch = database.getNextCrudTransaction()
            batch!!.crud[0].oldData shouldBe mapOf("name" to "entry", "content" to "content")
        }

    @Test
    fun includeOldValuesWithFilter() =
        databaseTest {
            database.updateSchema(
                Schema(
                    Table(
                        "lists",
                        listOf(Column.text("name"), Column.text("content")),
                        includeOld = IncludeOldOptions(columnFilter = listOf("name")),
                    ),
                ),
            )

            database.execute("INSERT INTO lists (id, name, content) VALUES (uuid(), ?, ?)", listOf("entry", "content"))
            database.execute("DELETE FROM ps_crud")
            database.execute("UPDATE lists SET name = ?, content = ?", listOf("new name", "new content"))

            val batch = database.getNextCrudTransaction()
            batch!!.crud[0].oldData shouldBe mapOf("name" to "entry")
        }

    @Test
    fun includeOldValuesWhenChanged() =
        databaseTest {
            database.updateSchema(
                Schema(
                    Table(
                        "lists",
                        listOf(Column.text("name"), Column.text("content")),
                        includeOld = IncludeOldOptions(onlyWhenChanged = true),
                    ),
                ),
            )

            database.execute("INSERT INTO lists (id, name, content) VALUES (uuid(), ?, ?)", listOf("entry", "content"))
            database.execute("DELETE FROM ps_crud")
            database.execute("UPDATE lists SET name = ?", listOf("new name"))

            val batch = database.getNextCrudTransaction()
            batch!!.crud[0].oldData shouldBe mapOf("name" to "entry")
        }

    @Test
    fun ignoreEmptyUpdate() =
        databaseTest {
            database.updateSchema(Schema(Table("lists", listOf(Column.text("name"), Column.text("content")), ignoreEmptyUpdate = true)))

            database.execute("INSERT INTO lists (id, name, content) VALUES (uuid(), ?, ?)", listOf("entry", "content"))
            database.execute("DELETE FROM ps_crud")
            database.execute("UPDATE lists SET name = ?", listOf("entry"))

            val batch = database.getNextCrudTransaction()
            batch shouldBe null
        }
}
