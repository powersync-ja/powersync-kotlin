package com.powersync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Schema
import com.powersync.testutils.UserRow
import com.powersync.testutils.waitFor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalKermitApi::class)
class DatabaseTest {
    private val logWriter =
        TestLogWriter(
            loggable = Severity.Debug,
        )

    private val logger =
        Logger(
            TestConfig(
                minSeverity = Severity.Debug,
                logWriterList = listOf(logWriter),
            ),
        )

    private lateinit var database: PowerSyncDatabase

    private fun openDB() =
        PowerSyncDatabase(
            factory = com.powersync.testutils.factory,
            schema = Schema(UserRow.table),
            dbFilename = "testdb",
            logger = logger,
        )

    @BeforeTest
    fun setupDatabase() {
        logWriter.reset()

        database = openDB()

        runBlocking {
            database.disconnectAndClear(true)
        }
    }

    @AfterTest
    fun tearDown() {
        runBlocking { database.disconnectAndClear(true) }
        com.powersync.testutils.cleanup("testdb")
    }

    @Test
    fun testLinksPowerSync() =
        runTest {
            database.get("SELECT powersync_rs_version();") { it.getString(0)!! }
        }

    @Test
    fun testWAL() =
        runTest {
            val mode =
                database.get(
                    "PRAGMA journal_mode",
                    mapper = { it.getString(0)!! },
                )
            assertEquals(mode, "wal")
        }

    @Test
    fun testFTS() =
        runTest {
            val mode =
                database.get(
                    "SELECT sqlite_compileoption_used('ENABLE_FTS5');",
                    mapper = { it.getLong(0)!! },
                )
            assertEquals(mode, 1)
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
    fun testTransactionReads() =
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
                        it.execute("syntax error, revert please (this is intentional from the unit test)")
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
    fun warnsMultipleInstances() =
        runTest {
            // Opens a second DB with the same database filename
            val db2 = openDB()
            waitFor {
                assertNotNull(
                    logWriter.logs.find {
                        it.message == PowerSyncDatabaseImpl.multipleInstancesMessage
                    },
                )
            }
            db2.close()
        }
}
