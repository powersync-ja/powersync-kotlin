package com.powersync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.turbineScope
import com.powersync.db.schema.Schema
import com.powersync.testutils.UserRow
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        runBlocking {
            database.disconnectAndClear(true)
            database.close()
        }
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
    fun testConcurrentReads() =
        runTest {
            database.execute(
                "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                listOf(
                    "steven",
                    "s@journeyapps.com",
                ),
            )

            val pausedTransaction = CompletableDeferred<Unit>()
            val transactionItemCreated = CompletableDeferred<Unit>()
            // Start a long running writeTransaction
            val transactionJob =
                async {
                    database.writeTransaction { tx ->
                        // Create another user
                        // External readers should not see this user while the transaction is open
                        tx.execute(
                            "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                            listOf(
                                "steven",
                                "s@journeyapps.com",
                            ),
                        )

                        transactionItemCreated.complete(Unit)

                        // Block this transaction until we free it
                        runBlocking {
                            pausedTransaction.await()
                        }
                    }
                }

            // Make sure to wait for the item to have been created in the transaction
            transactionItemCreated.await()
            // Try and read while the write transaction is busy
            val result = database.getAll("SELECT * FROM users") { UserRow.from(it) }
            // The transaction is not commited yet, we should only read 1 user
            assertEquals(result.size, 1)

            // Let the transaction complete
            pausedTransaction.complete(Unit)
            transactionJob.await()

            val afterTx = database.getAll("SELECT * FROM users") { UserRow.from(it) }
            assertEquals(afterTx.size, 2)
        }

    @Test
    fun transactionReads() =
        runTest {
            database.execute(
                "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                listOf(
                    "steven",
                    "s@journeyapps.com",
                ),
            )

            database.writeTransaction { tx ->
                val userCount =
                    tx.getAll("SELECT COUNT(*) as count FROM users") { cursor -> cursor.getLong(0)!! }
                assertEquals(userCount[0], 1)

                tx.execute(
                    "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                    listOf(
                        "steven",
                        "s@journeyapps.com",
                    ),
                )

                // Getters inside the transaction should be able to see the latest update
                val userCount2 =
                    tx.getAll("SELECT COUNT(*) as count FROM users") { cursor -> cursor.getLong(0)!! }
                assertEquals(userCount2[0], 2)
            }
        }

    @Test
    fun openDBWithDirectory() =
        runTest {
            val tempDir =
                InstrumentationRegistry
                    .getInstrumentation()
                    .targetContext.cacheDir.canonicalPath
            val dbFilename = "testdb"

            val db =
                PowerSyncDatabase(
                    factory = DatabaseDriverFactory(InstrumentationRegistry.getInstrumentation().targetContext),
                    schema = Schema(UserRow.table),
                    dbDirectory = tempDir,
                    dbFilename = dbFilename,
                )

            val path = db.get("SELECT file FROM pragma_database_list;") { it.getString(0)!! }

            assertEquals(path.contains(tempDir), true)

            db.close()
        }

    @Test
    fun readConnectionsReadOnly() =
        runTest {
            val exception =
                assertThrows(PowerSyncException::class.java) {
                    // This version of assertThrows does not support suspending functions
                    runBlocking {
                        database.getOptional(
                            """
                            INSERT INTO 
                                 users (id, name, email)
                             VALUES
                                 (uuid(), ?, ?) 
                             RETURNING *
                            """.trimIndent(),
                            parameters = listOf("steven", "steven@journeyapps.com"),
                        ) {}
                    }
                }
            // The exception messages differ slightly between drivers
            assertEquals(exception.message!!.contains("write a readonly database"), true)
        }

    @Test
    fun canCreateTemporaryTable() = runTest {
        database.execute("PRAGMA temp_store = 1;") // Store temporary data as files
        database.execute("CREATE TEMP TABLE my_tbl (content ANY) STRICT;")
        for (i in 0..128) {
            database.execute("INSERT INTO my_tbl VALUES (randomblob(1024 * 1024))")
        }
    }
}
