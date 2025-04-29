package com.powersync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import com.powersync.db.ActiveDatabaseGroup
import com.powersync.db.schema.Schema
import com.powersync.testutils.UserRow
import com.powersync.testutils.databaseTest
import com.powersync.testutils.getTempDir
import com.powersync.testutils.isIOS
import com.powersync.testutils.waitFor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalKermitApi::class)
class DatabaseTest {
    @Test
    fun testLinksPowerSync() =
        databaseTest {
            database.get("SELECT powersync_rs_version();") { it.getString(0)!! }
        }

    @Test
    fun testWAL() =
        databaseTest {
            val mode =
                database.get(
                    "PRAGMA journal_mode",
                    mapper = { it.getString(0)!! },
                )
            mode shouldBe "wal"
        }

    @Test
    fun testFTS() =
        databaseTest {
            val mode =
                database.get(
                    "SELECT sqlite_compileoption_used('ENABLE_FTS5');",
                    mapper = { it.getLong(0)!! },
                )
            mode shouldBe 1
        }

    @Test
    fun testConcurrentReads() =
        databaseTest {
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
                scope.async {
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
        databaseTest {
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
        databaseTest {
            turbineScope {
                val query = database.watch("SELECT * FROM users") { UserRow.from(it) }.testIn(this)

                // Wait for initial query
                query.awaitItem() shouldHaveSize 0

                database.execute(
                    "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                    listOf("Test", "test@example.org"),
                )
                query.awaitItem() shouldHaveSize 1

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

                query.awaitItem() shouldHaveSize 3

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
                query.awaitItem() shouldHaveSize 4

                query.expectNoEvents()
                query.cancel()
            }
        }

    @Test
    fun testTableChangesUpdates() =
        databaseTest {
            turbineScope {
                val query =
                    database
                        .onChange(
                            tables = setOf("users"),
                        ).testIn(this)

                database.execute(
                    "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                    listOf("Test", "test@example.org"),
                )

                var changeSet = query.awaitItem()
                // The initial result
                changeSet.count() shouldBe 0

                changeSet = query.awaitItem()
                changeSet.count() shouldBe 1
                changeSet.contains("users") shouldBe true

                query.cancel()
            }
        }

    @Test
    fun testClosingReadPool() =
        databaseTest {
            val pausedLock = CompletableDeferred<Unit>()
            val inLock = CompletableDeferred<Unit>()
            // Request a lock
            val lockJob =
                scope.async {
                    database.readLock {
                        inLock.complete(Unit)
                        runBlocking {
                            pausedLock.await()
                        }
                    }
                }

            // Wait for the lock to be active
            inLock.await()

            // Close the database. This should close the read pool
            // The pool should wait for jobs to complete before closing
            val closeJob =
                scope.async {
                    database.close()
                }

            // Wait a little for testing
            // Spawns in a different context for the delay to actually take effect
            scope.async { withContext(Dispatchers.Default) { delay(500) } }.await()

            // The database should not close yet
            assertEquals(actual = database.closed, expected = false)

            // Any new readLocks should throw
            val exception = shouldThrow<PowerSyncException> { database.readLock {} }
            exception.message shouldBe "Cannot process connection pool request"

            // Release the lock
            pausedLock.complete(Unit)
            lockJob.await()
            closeJob.await()

            database.closed shouldBe true
        }

    @Test
    fun openDBWithDirectory() =
        databaseTest {
            val tempDir =
                if (isIOS()) {
                    null
                } else {
                    getTempDir()
                }

            if (tempDir == null) {
                // SQLiteR, which is used on iOS, does not support opening dbs from directories
                return@databaseTest
            }

            // On platforms that support it, openDatabase() from our test utils should use a temporary
            // location.
            val path = database.get("SELECT file FROM pragma_database_list;") { it.getString(0)!! }
            path shouldContain tempDir
        }

    @Test
    fun warnsMultipleInstances() =
        databaseTest {
            // Opens a second DB with the same database filename
            val db2 = openDatabase()
            waitFor {
                assertNotNull(
                    logWriter.logs.find {
                        it.message == ActiveDatabaseGroup.multipleInstancesMessage
                    },
                )
            }
            db2.close()
        }

    @Test
    fun readConnectionsReadOnly() =
        databaseTest {
            val exception =
                shouldThrow<PowerSyncException> {
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

            // The exception messages differ slightly between drivers
            exception.message shouldContain "write a readonly database"
        }

    @Test
    fun basicReadTransaction() =
        databaseTest {
            val count =
                database.readTransaction { it ->
                    it.get("SELECT COUNT(*) from users") { it.getLong(0)!! }
                }
            count shouldBe 0
        }

    @Test
    fun localOnlyCRUD() =
        databaseTest {
            database.updateSchema(
                schema =
                    Schema(
                        UserRow.table.copy(
                            // Updating the existing "users" view to localOnly causes an error
                            // no such table: main.ps_data_local__users.
                            // Perhaps this is a bug in the core extension
                            name = "local_users",
                            localOnly = true,
                        ),
                    ),
            )

            database.execute(
                """
                    INSERT INTO
                       local_users (id, name, email)
                    VALUES
                     (uuid(), "one", "two@t.com")
                    """,
            )

            val count = database.get("SELECT COUNT(*) FROM local_users") { it.getLong(0)!! }
            count shouldBe 1

            // No CRUD entries should be present for local only tables
            val crudItems = database.getAll("SELECT id from ps_crud") { it.getLong(0)!! }
            crudItems shouldHaveSize 0
        }

    @Test
    fun insertOnlyCRUD() =
        databaseTest {
            database.updateSchema(schema = Schema(UserRow.table.copy(insertOnly = true)))

            database.execute(
                """
                    INSERT INTO
                       users (id, name, email)
                    VALUES
                     (uuid(), "one", "two@t.com")
                    """,
            )

            val crudItems = database.getAll("SELECT id from ps_crud") { it.getLong(0)!! }
            crudItems shouldHaveSize 1

            val count = database.get("SELECT COUNT(*) from users") { it.getLong(0)!! }
            count shouldBe 0
        }

    @Test
    fun viewOverride() =
        databaseTest {
            database.updateSchema(schema = Schema(UserRow.table.copy(viewNameOverride = "people")))

            database.execute(
                """
                    INSERT INTO
                       people (id, name, email)
                    VALUES
                     (uuid(), "one", "two@t.com")
                    """,
            )

            val crudItems = database.getAll("SELECT id from ps_crud") { it.getLong(0)!! }
            crudItems shouldHaveSize 1

            val count = database.get("SELECT COUNT(*) from people") { it.getLong(0)!! }
            count shouldBe 1
        }

    @Test
    fun testCrudTransaction() =
        databaseTest {
            database.execute(
                "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                listOf("a", "a@example.org"),
            )

            database.writeTransaction {
                it.execute(
                    "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                    listOf("b", "b@example.org"),
                )
                it.execute(
                    "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                    listOf("c", "c@example.org"),
                )
            }

            var transaction = database.getNextCrudTransaction()
            transaction!!.crud shouldHaveSize 1
            transaction.complete(null)

            transaction = database.getNextCrudTransaction()
            transaction!!.crud shouldHaveSize 2
            transaction.complete(null)

            database.getNextCrudTransaction() shouldBe null
        }

    @Test
    fun testCrudBatch() =
        databaseTest {
            database.execute(
                "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                listOf("a", "a@example.org"),
            )

            database.writeTransaction {
                it.execute(
                    "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                    listOf("b", "b@example.org"),
                )
                it.execute(
                    "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                    listOf("c", "c@example.org"),
                )
            }

            // Purposely limit to less than the number of available ops
            var batch = database.getCrudBatch(2) ?: error("Batch should not be null")
            batch.hasMore shouldBe true
            batch.crud shouldHaveSize 2
            batch.complete(null)

            batch = database.getCrudBatch(1000) ?: error("Batch should not be null")
            batch.crud shouldHaveSize 1
            batch.complete(null)

            database.getCrudBatch() shouldBe null
        }
}
