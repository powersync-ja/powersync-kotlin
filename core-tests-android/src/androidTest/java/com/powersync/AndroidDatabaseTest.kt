package com.powersync

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.turbineScope
import com.powersync.db.schema.Schema
import com.powersync.testutils.UserRow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.After
import kotlinx.coroutines.delay
import kotlinx.coroutines.*


import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before

@RunWith(AndroidJUnit4::class)
class AndroidDatabaseTest {
    private lateinit var database: PowerSyncDatabase

    @Before
    fun setupDatabase() {
        database =
            PowerSyncDatabase(
                factory = DatabaseDriverFactory(InstrumentationRegistry.getInstrumentation().targetContext),
                schema = Schema(UserRow.table),
                dbFilename = "testdb",
            )

        runBlocking {
            database.disconnectAndClear(true)
        }
    }

    @After
    fun tearDown() {
        runBlocking { database.disconnectAndClear(true) }
    }

    @Test
    fun testLinksPowerSync() =
        runTest {
            database.get("SELECT powersync_rs_version() AS r;") { it.getString(0)!! }
        }

    @Test
    fun testTableUpdates() =
        runTest {
            turbineScope {
                val query = database.watch("SELECT * FROM users") { UserRow.from(it) }.testIn(this)

                // Wait for initial query
                assertEquals(0, query.awaitItem().size)

                database.execute(
                    "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                    listOf("Test", "test@example.org"),
                )
                assertEquals(1, query.awaitItem().size)

                database.writeTransaction {
                    it.execute(
                        "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                        listOf("Test2", "test2@example.org"),
                    )
                    it.execute(
                        "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                        listOf("Test3", "test3@example.org"),
                    )
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

                database.execute(
                    "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                    listOf("Test4", "test4@example.org"),
                )
                assertEquals(4, query.awaitItem().size)

                query.expectNoEvents()
                query.cancel()
            }
        }

    @Test
    fun testThrottledTableUpdates() =
        runTest {
            turbineScope {
                // Avoids skipping delays
                withContext(Dispatchers.Default) {
                    val query =
                        database.watch(
                            "SELECT * FROM users",
                            throttleMs = 1000
                        ) { UserRow.from(it) }.testIn(this)

                    // Wait for initial query
                    assertEquals(0, query.awaitItem().size)

                    database.execute(
                        "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                        listOf("Test", "test@example.org"),
                    )

                    database.writeTransaction {
                        it.execute(
                            "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                            listOf("Test2", "test2@example.org"),
                        )
                        it.execute(
                            "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                            listOf("Test3", "test3@example.org"),
                        )
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

                    database.execute(
                        "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                        listOf("Test4", "test4@example.org"),
                    )
                    assertEquals(4, query.awaitItem().size)

                    query.expectNoEvents()
                    query.cancel()

                }

            }
        }
}