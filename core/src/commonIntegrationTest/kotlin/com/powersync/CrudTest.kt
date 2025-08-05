package com.powersync

import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import com.powersync.db.schema.TrackPreviousValuesOptions
import com.powersync.testutils.databaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CrudTest {
    @Test
    fun includeMetadata() =
        databaseTest {
            database.updateSchema(Schema(Table("lists", listOf(Column.text("name")), trackMetadata = true)))

            database.execute("INSERT INTO lists (id, name, _metadata) VALUES (uuid(), ?, ?)", listOf("entry", "so meta"))
            val batch = database.getNextCrudTransaction()
            batch!!.crud[0].metadata shouldBe "so meta"
        }

    @Test
    fun includeOldValues() =
        databaseTest {
            database.updateSchema(
                Schema(
                    Table("lists", listOf(Column.text("name"), Column.text("content")), trackPreviousValues = TrackPreviousValuesOptions()),
                ),
            )

            database.execute("INSERT INTO lists (id, name, content) VALUES (uuid(), ?, ?)", listOf("entry", "content"))
            database.execute("DELETE FROM ps_crud")
            database.execute("UPDATE lists SET name = ?", listOf("new name"))

            val batch = database.getNextCrudTransaction()
            batch!!.crud[0].previousValues shouldBe mapOf("name" to "entry", "content" to "content")
        }

    @Test
    fun includeOldValuesWithFilter() =
        databaseTest {
            database.updateSchema(
                Schema(
                    Table(
                        "lists",
                        listOf(Column.text("name"), Column.text("content")),
                        trackPreviousValues = TrackPreviousValuesOptions(columnFilter = listOf("name")),
                    ),
                ),
            )

            database.execute("INSERT INTO lists (id, name, content) VALUES (uuid(), ?, ?)", listOf("entry", "content"))
            database.execute("DELETE FROM ps_crud")
            database.execute("UPDATE lists SET name = ?, content = ?", listOf("new name", "new content"))

            val batch = database.getNextCrudTransaction()
            batch!!.crud[0].previousValues shouldBe mapOf("name" to "entry")
        }

    @Test
    fun includeOldValuesWhenChanged() =
        databaseTest {
            database.updateSchema(
                Schema(
                    Table(
                        "lists",
                        listOf(Column.text("name"), Column.text("content")),
                        trackPreviousValues = TrackPreviousValuesOptions(onlyWhenChanged = true),
                    ),
                ),
            )

            database.execute("INSERT INTO lists (id, name, content) VALUES (uuid(), ?, ?)", listOf("entry", "content"))
            database.execute("DELETE FROM ps_crud")
            database.execute("UPDATE lists SET name = ?", listOf("new name"))

            val batch = database.getNextCrudTransaction()
            batch!!.crud[0].previousValues shouldBe mapOf("name" to "entry")
        }

    @Test
    fun ignoreEmptyUpdate() =
        databaseTest {
            database.updateSchema(Schema(Table("lists", listOf(Column.text("name"), Column.text("content")), ignoreEmptyUpdates = true)))

            database.execute("INSERT INTO lists (id, name, content) VALUES (uuid(), ?, ?)", listOf("entry", "content"))
            database.execute("DELETE FROM ps_crud")
            database.execute("UPDATE lists SET name = ?", listOf("entry"))

            val batch = database.getNextCrudTransaction()
            batch shouldBe null
        }

    @Test
    fun typedUpdates() =
        databaseTest {
            database.updateSchema(
                Schema(
                    Table(
                        "foo",
                        listOf(
                            Column.text("a"),
                            Column.integer("b"),
                            Column.integer("c"),
                        ),
                        trackPreviousValues = TrackPreviousValuesOptions(onlyWhenChanged = true),
                    ),
                ),
            )

            database.writeTransaction { tx ->
                tx.execute(
                    "INSERT INTO foo (id,a,b,c) VALUES (uuid(), ?, ?, ?)",
                    listOf(
                        "text",
                        42,
                        13.37,
                    ),
                )
                tx.execute(
                    "UPDATE foo SET a = ?, b = NULL",
                    listOf(
                        "te\"xt",
                    ),
                )
            }

            var batch = database.getNextCrudTransaction()!!
            batch.crud[0].data shouldBe
                mapOf(
                    "a" to "text",
                    "b" to 42,
                    "c" to 13.37,
                )
            batch.crud[0].typedPreviousValues shouldBe null

            batch.crud[1].data shouldBe
                mapOf(
                    "a" to "te\"xt",
                    "b" to null,
                )
            batch.crud[1].typedPreviousValues shouldBe
                mapOf(
                    "a" to "text",
                    "b" to 42,
                )

            database.execute("DELETE FROM ps_crud")
            database.execute(
                "UPDATE foo SET a = ?",
                listOf("42"),
            )

            batch = database.getNextCrudTransaction()!!
            batch.crud[0].data shouldBe
                mapOf(
                    "a" to "42", // Not an integer!
                )
        }
}
