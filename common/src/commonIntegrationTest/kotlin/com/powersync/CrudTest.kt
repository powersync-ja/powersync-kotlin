package com.powersync

import com.powersync.db.crud.UpdateType
import com.powersync.db.schema.Column
import com.powersync.db.schema.RawTable
import com.powersync.db.schema.RawTableSchema
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import com.powersync.db.schema.TableOptions
import com.powersync.db.schema.TrackPreviousValuesOptions
import com.powersync.testutils.databaseTest
import io.kotest.matchers.collections.shouldHaveSize
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
            batch.crud[0].opData?.typed shouldBe
                mapOf(
                    "a" to "text",
                    "b" to 42,
                    "c" to 13.37,
                )
            batch.crud[0].previousValues shouldBe null

            batch.crud[1].opData shouldBe
                mapOf(
                    "a" to "te\"xt",
                    "b" to null,
                )
            batch.crud[1].opData?.typed shouldBe
                mapOf(
                    "a" to "te\"xt",
                    "b" to null,
                )
            batch.crud[1].previousValues?.typed shouldBe
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
            batch.crud[0].opData?.typed shouldBe
                mapOf(
                    "a" to "42", // Not an integer!
                )
        }

    @Test
    fun rawTableInferredCrudTrigger() =
        databaseTest(createInitialDatabase = false) {
            val table = RawTable("users", RawTableSchema())
            database = openDatabase(Schema(tables = listOf(), rawTables = listOf(table)))

            database.execute("CREATE TABLE users (id TEXT, name TEXT);")
            database.execute(
                "SELECT powersync_create_raw_table_crud_trigger(?, ?, ?)",
                listOf(
                    table.jsonDescription(),
                    "users_insert",
                    "INSERT",
                ),
            )

            database.execute(
                "INSERT INTO users (id, name) VALUES (?, ?);",
                listOf(
                    "id",
                    "user",
                ),
            )

            val tx = database.getNextCrudTransaction()!!
            tx.crud shouldHaveSize 1
            val write = tx.crud[0]
            write.op shouldBe UpdateType.PUT
            write.table shouldBe "users"
            write.id shouldBe "id"
            write.opData shouldBe mapOf("name" to "user")
        }

    @Test
    fun rawTableOptions() =
        databaseTest(createInitialDatabase = false) {
            val table =
                RawTable(
                    "sync_name",
                    RawTableSchema(
                        tableName = "users",
                        syncedColumns = listOf("name"),
                        options =
                            TableOptions(
                                ignoreEmptyUpdates = true,
                                trackPreviousValues = TrackPreviousValuesOptions(),
                            ),
                    ),
                )
            database = openDatabase(Schema(tables = listOf(), rawTables = listOf(table)))

            database.execute("CREATE TABLE users (id TEXT, name TEXT, local TEXT);")
            database.execute(
                "INSERT INTO users (id, name, local) VALUES (?, ?, ?)",
                listOf(
                    "id",
                    "name",
                    "local",
                ),
            )

            database.execute(
                "SELECT powersync_create_raw_table_crud_trigger(?, ?, ?)",
                listOf(
                    table.jsonDescription(),
                    "users_update",
                    "UPDATE",
                ),
            )

            database.execute("UPDATE users SET name = ?, local = ?", listOf("updated_name", "updated_local"))

            // This should not generate a CRUD entry because the only syned column is not affected.
            database.execute("UPDATE users SET name = ?, local = ?", listOf("updated_name", "updated_local_2"))

            val tx = database.getNextCrudTransaction()!!
            tx.crud shouldHaveSize 1
            val write = tx.crud[0]
            write.op shouldBe UpdateType.PATCH
            write.id shouldBe "id"
            // These should not include the local-only column
            write.opData shouldBe mapOf("name" to "updated_name")
            write.previousValues shouldBe mapOf("name" to "name")
        }
}
