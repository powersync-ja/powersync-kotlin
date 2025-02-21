package com.powersync

import app.cash.turbine.turbineScope
import com.powersync.db.SqlCursor
import com.powersync.db.getString
import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import com.powersync.testutils.cleanup
import com.powersync.testutils.factory
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseTest {
    private lateinit var database: PowerSyncDatabase

    @BeforeTest
    fun setupDatabase() {
        database = PowerSyncDatabase(
            factory = factory,
            schema = Schema(
                Table(name = "users", columns = listOf(Column.text("name"), Column.text("email")))
            ),
            dbFilename = "testdb"
        )
    }

    @AfterTest
    fun tearDown() {
        cleanup("testdb")
    }

    @Test
    fun testLinksPowerSync() = runTest {
        database.get("SELECT powersync_rs_version() AS r;") { it.getString(0)!! }
    }

    @Test
    fun testTableUpdates() = runTest {
        turbineScope {
            val query = database.watch("SELECT * FROM users") { User.from(it) }.testIn(this)

            // Wait for initial query
            assertEquals(0, query.awaitItem().size)

            database.execute("INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)", listOf("Test", "test@example.org"))
            assertEquals(1, query.awaitItem().size)

            database.writeTransaction {
                it.execute("INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)", listOf("Test2", "test2@example.org"))
                it.execute("INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)", listOf("Test3", "test3@example.org"))
            }

            assertEquals(3, query.awaitItem().size)

            try {
                database.writeTransaction {
                    it.execute("DELETE FROM users;")
                    it.execute("syntax error, revert please")
                }
            } catch (e: Exception) {
                // Ignore
            }

            database.execute("INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)", listOf("Test4", "test4@example.org"))
            assertEquals(4, query.awaitItem().size)

            query.expectNoEvents()
            query.cancel()
        }
    }

    private data class User(val id: String, val name: String, val email: String) {
        companion object {
            fun from(cursor: SqlCursor): User = User(
                id = cursor.getString("id"),
                name = cursor.getString("name"),
                email = cursor.getString("email")
            )
        }
    }
}
