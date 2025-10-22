package com.powersync.integrations.sqldelight

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.turbine.turbineScope
import com.powersync.PowerSyncDatabase
import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import com.powersync.inMemory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.properties.shouldHaveValue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SqlDelightTest {
    @Test
    fun simpleQueries() =
        databaseTest { powersync ->
            val db = TestDatabase(PowerSyncDriver(powersync, this))
            val query = db.todosQueries.all()
            query.awaitAsList() shouldBe emptyList()

            db.todosQueries.create("my title", "my content")
            query.awaitAsList().map { it.title } shouldBe listOf("my title")
        }

    @Test
    fun writeCreatesCrudEntry() =
        databaseTest { powersync ->
            val db = TestDatabase(PowerSyncDriver(powersync, this))
            db.todosQueries.create("my title", "my content")

            val tx = powersync.getNextCrudTransaction()!!
            val item = tx.crud.single()
            item::table shouldHaveValue "todos"
            item.opData shouldBe mapOf("title" to "my title", "content" to "my content")
        }

    @Test
    fun powerSyncUpdatesSqlDelight() =
        databaseTest { powersync ->
            val db = TestDatabase(PowerSyncDriver(powersync, this))
            turbineScope {
                val turbine =
                    db.todosQueries
                        .all()
                        .asFlow()
                        .mapToList(currentCoroutineContext())
                        .testIn(this)
                turbine.awaitItem() shouldBe emptyList()

                // Emulate data from the PowerSync service
                powersync.execute(
                    "INSERT INTO ps_data__todos (id, data) VALUES (?, ?)",
                    listOf("server_id", """{"title": "from service", "content": "synced content"}"""),
                )

                val row = turbine.awaitItem().single()
                row::title shouldHaveValue "from service"
                row::content shouldHaveValue "synced content"

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun sqlDelightUpdatesPowerSync() =
        databaseTest { powersync ->
            val db = TestDatabase(PowerSyncDriver(powersync, this))
            turbineScope {
                val turbine = powersync.watch("SELECT title FROM todos") { it.getString(0)!! }.testIn(this)
                turbine.awaitItem() shouldBe emptyList()

                db.todosQueries.create("title", "content")
                turbine.awaitItem() shouldBe listOf("title")

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testReturningQuery() =
        databaseTest { powersync ->
            val db = TestDatabase(PowerSyncDriver(powersync, this))
            turbineScope {
                db.todosQueries.create("title", "content")

                val query =
                    db.todosQueries
                        .all()
                        .asFlow()
                        .mapToList(currentCoroutineContext())
                        .testIn(this)
                query.awaitItem() shouldHaveSize 1

                val updatedItem = db.todosQueries.update().awaitAsOne()
                updatedItem.content shouldBe "contenttitle"
                query.awaitItem() shouldBe listOf(updatedItem)
                query.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testTransaction() =
        databaseTest { powersync ->
            val db = TestDatabase(PowerSyncDriver(powersync, this))
            turbineScope {
                val turbine = powersync.watch("SELECT title FROM todos") { it.getString(0)!! }.testIn(this)
                turbine.awaitItem() shouldBe emptyList()

                db.transaction {
                    db.todosQueries.create("first", "first content")
                    db.todosQueries.create("second", "second content")
                }

                // Should commit atomically
                turbine.awaitItem() shouldBe listOf("first", "second")
                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testTransactionRollback() =
        databaseTest { powersync ->
            val db = TestDatabase(PowerSyncDriver(powersync, this))
            shouldThrow<Exception> {
                db.transaction {
                    db.todosQueries.create("first", "first content")
                    throw Exception("Test exception for rollback")
                }
            }

            db.todosQueries.all().awaitAsList() shouldHaveSize 0
        }
}

private fun databaseTest(body: suspend TestScope.(PowerSyncDatabase) -> Unit) {
    runTest {
        val db =
            PowerSyncDatabase.inMemory(
                scope = this,
                schema =
                    Schema(
                        Table(
                            "todos",
                            listOf(
                                Column.text("title"),
                                Column.text("content"),
                            ),
                        ),
                    ),
            )

        body(db)
        db.close()
    }
}
