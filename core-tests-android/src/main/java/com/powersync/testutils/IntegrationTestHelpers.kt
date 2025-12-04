package com.powersync.testutils

import android.content.Context
import app.cash.turbine.turbineScope
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.db.schema.Schema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/**
 * Everything needed to run integration tests, except the actual test framework.
 *
 * This allows us to only keep this class active with proguard, and assert that minifying the PowerSync
 * SDK with Proguard works as intended.
 */
class IntegrationTestHelpers(private val context: Context) {

    private lateinit var database: PowerSyncDatabase

    fun setup() {
        runBlocking {
            database =
                PowerSyncDatabase(
                    factory = DatabaseDriverFactory(context),
                    schema = Schema(UserRow.table),
                    dbFilename = "testdb",
                )
        }
    }

    fun tearDown() {
        runBlocking {
            database.disconnectAndClear(true)
            database.close()
        }
    }


    fun testLinksPowerSync() =
        runTest {
            database.get("SELECT powersync_rs_version() AS r;") { it.getString(0)!! }
        }


    fun testTableUpdates() =
        runTest {
            turbineScope {
                val query = database.watch("SELECT * FROM users") { UserRow.from(it) }.testIn(this)

                // Wait for initial query
                check(query.awaitItem().isEmpty()) { "Expected initial select to be empty" }

                database.execute(
                    "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                    listOf("Test", "test@example.org"),
                )

                check(query.awaitItem().size == 1) { "Expected second select to emit one item" }

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

                check(query.awaitItem().size == 3) { "Expected three items after transaction" }

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
                check(query.awaitItem().size == 4) { "Expected four items after second transaction" }

                query.expectNoEvents()
                query.cancel()
            }
        }

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
            check(result.size == 1) { "Expected one user while transaction is still active" }

            // Let the transaction complete
            pausedTransaction.complete(Unit)
            transactionJob.await()

            val afterTx = database.getAll("SELECT * FROM users") { UserRow.from(it) }
            check(afterTx.size == 2) { "Expected two users after transaction" }
        }

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

                check(userCount[0] == 1L)

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
                check(userCount2[0] == 2L)
            }
        }

    fun openDBWithDirectory() =
        runTest {
            val tempDir = context.cacheDir.canonicalPath
            val dbFilename = "testdb"

            val db =
                PowerSyncDatabase(
                    factory = DatabaseDriverFactory(context),
                    schema = Schema(UserRow.table),
                    dbDirectory = tempDir,
                    dbFilename = dbFilename,
                )

            val path = db.get("SELECT file FROM pragma_database_list;") { it.getString(0)!! }

            check(path.contains(tempDir))

            db.close()
        }

    fun readConnectionsReadOnly() =
        runTest {
            val exception = try {
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

                throw IllegalStateException("Expected write to throw")
            } catch (expected: PowerSyncException) {
                expected
            }

            // The exception messages differ slightly between drivers, so we only use contains
            check(exception.message!!.contains("write a readonly database")) {
                "${exception.message} should contain 'write a readonly database'"
            }
        }

    fun canUseTempStore() = runTest {
        database.execute("PRAGMA temp_store = 1;") // Store temporary data as files
        database.execute("CREATE TEMP TABLE foo (bar TEXT);")
        val data = "new row".repeat(100)
        repeat(10000) {
            database.execute("INSERT INTO foo VALUES (?)", parameters = listOf(data))
        }
    }
}
