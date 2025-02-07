package com.powersync.psdb

import co.touchlab.stately.concurrency.AtomicBoolean
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.db.getString
import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

expect abstract class RobolectricTest()

expect fun cleanupDb()

class PowerSyncDatabaseTest : RobolectricTest() {
    private lateinit var database: PowerSyncDatabase

    @BeforeTest
    fun testOk() {
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
        runBlocking {
            database.disconnectAndClear()
        }
        cleanupDb()
    }

    @Test
    fun testInsertAndGet() {
        runBlocking {
            database.execute(
                sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                parameters = listOf("1", "Test User", "test@example.com")
            )

            val user = database.get(
                sql = "SELECT id, name, email FROM users WHERE id = ?",
                parameters = listOf("1")
            ) { cursor ->
                listOf(
                    cursor.getString(0)!!,
                    cursor.getString(1)!!,
                    cursor.getString(2)!!
                )
            }

            assertEquals(user[0], "1")
            assertEquals(user[1], "Test User")
            assertEquals(user[2], "test@example.com")
        }
    }

    @Test
    fun testWriteTransactionWithInserts() {
        runBlocking {
            database.writeTransaction { tx ->
                tx.execute(
                    sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                    parameters = listOf("2", "Another User", "another@example.com")
                )
                tx.execute(
                    sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                    parameters = listOf("3", "Third User", "third@example.com")
                )
            }

            val users = database.getAll(
                sql = "SELECT id, name, email FROM users WHERE id IN (?, ?)",
                parameters = listOf("2", "3")
            ) { cursor ->
                listOf(
                    cursor.getString(0)!!,
                    cursor.getString(1)!!,
                    cursor.getString(2)!!
                )
            }

            assertEquals(users.size, 2)
            assertEquals(users[0][0], "2")
            assertEquals(users[0][1], "Another User")
            assertEquals(users[0][2], "another@example.com")
            assertEquals(users[1][0], "3")
            assertEquals(users[1][1], "Third User")
            assertEquals(users[1][2], "third@example.com")
        }
    }

    @Test
    fun testInsertAndUpdateTransaction() {
        runBlocking {
            database.writeTransaction { tx ->
                // Insert a new user
                tx.execute(
                    sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                    parameters = listOf("4", "Initial User", "initial@example.com")
                )
                // Update the user's name and email
                tx.execute(
                    sql = "UPDATE users SET name = ?, email = ? WHERE id = ?",
                    parameters = listOf("Updated User", "updated@example.com", "4")
                )
            }

            val user = database.get(
                sql = "SELECT id, name, email FROM users WHERE id = ?",
                parameters = listOf("4")
            ) { cursor ->
                listOf(
                    cursor.getString(0)!!,
                    cursor.getString(1)!!,
                    cursor.getString(2)!!
                )
            }

            assertEquals(user[0], "4")
            assertEquals(user[1], "Updated User")
            assertEquals(user[2], "updated@example.com")
        }
    }

    @Test
    fun testInsertAndDeleteTransaction() {
        runBlocking {
            database.writeTransaction { tx ->
                // Insert a new user
                tx.execute(
                    sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                    parameters = listOf("5", "User to Delete", "delete@example.com")
                )
                // Delete the user
                tx.execute(
                    sql = "DELETE FROM users WHERE id = ?",
                    parameters = listOf("5")
                )
            }

            val user = database.getAll(
                sql = "SELECT id, name, email FROM users WHERE id = ?",
                parameters = listOf("5")
            ) { cursor ->
                listOf(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2)
                )
            }

            assertTrue(user.isEmpty())
        }
    }

    @Test
    fun testInsertAndGetByColumnName() {
        runBlocking {
            database.execute(
                sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                parameters = listOf("6", "Column Name User", "columnname@example.com")
            )

            val user = database.get(
                sql = "SELECT id, name, email FROM users WHERE id = ?",
                parameters = listOf("6")
            ) { cursor ->
                listOf(
                    cursor.getString("id"),
                    cursor.getString("name"),
                    cursor.getString("email")
                )
            }

            assertEquals(user[0], "6")
            assertEquals(user[1], "Column Name User")
            assertEquals(user[2], "columnname@example.com")
        }
    }

    @Test
    fun testAccessInvalidColumnName() {
        runBlocking {
            database.execute(
                sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                parameters = listOf("7", "Hello", "invalidcolumn@example.com")
            )
            val user = database.get(
                sql = "SELECT id, name, email FROM users WHERE id = ?",
                parameters = listOf("7")
            ) { cursor ->
                listOf(
                    cursor.getString("id"),
                    cursor.getString("name"),
                    cursor.getString("email")
                )
            }
            assertEquals(user[0], "7")
            assertEquals(user[1], "Hello")
            assertEquals(user[2], "invalidcolumn@example.com")

            assertFails {
                database.get(
                    sql = "SELECT id, name, email FROM users WHERE id = ?",
                    parameters = listOf("7")
                ) { cursor ->
                    listOf(
                        cursor.getString("invalid_column_name")
                    )
                }
            }
        }
    }

    @Test
    fun testQueryOutsideTransaction() {
        val isDone = AtomicBoolean(false)
        runBlocking {
            val txJob = launch {
                database.writeTransaction { tx ->
                    // Insert a new user within the transaction
                    tx.execute(
                        sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                        parameters = listOf("8", "Transaction User", "transaction@example.com")
                    )
                    // This isn't great, but it's a test.
                    while (!isDone.value) {
                    }
                }
            }

            suspend fun userListQuery(): List<List<String?>> {
                return database.getAll(
                    sql = "SELECT id, name, email FROM users WHERE id = ?",
                    parameters = listOf("8")
                ) { cursor ->
                    listOf(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2)
                    )
                }
            }

            // Query outside the transaction should not see the new user
            assertEquals(userListQuery().size, 0)

            isDone.value = true

            txJob.join()

            // Query after the transaction is complete should see the new user
            assertEquals(userListQuery().size, 1)
        }
    }

    @Test
    fun testQueryNonExistentTable() {
        runBlocking {
            assertFailsWith(PowerSyncException::class) {
                database.get(
                    sql = "SELECT * FROM non_existent_table",
                    parameters = emptyList()
                ) { cursor ->
                    emptyList<String>()
                }
            }
        }
    }

    @Test
    fun testBulkInsertTransaction() {
        runBlocking {
            database.writeTransaction { tx ->
                for (i in 1..100) {
                    tx.execute(
                        sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                        parameters = listOf(i.toString(), "User $i", "user$i@example.com")
                    )
                }
            }

            val users = database.getAll(
                sql = "SELECT id, name, email FROM users",
                parameters = emptyList()
            ) { cursor ->
                listOf(
                    cursor.getString(0)!!,
                    cursor.getString(1)!!,
                    cursor.getString(2)!!
                )
            }

            assertEquals(users.size, 100)
            for (i in 1..100) {
                assertEquals(users[i - 1][0], i.toString())
                assertEquals(users[i - 1][1], "User $i")
                assertEquals(users[i - 1][2], "user$i@example.com")
            }
        }
    }

    @Test
    fun testInsertWithNullValues() {
        runBlocking {
            database.writeTransaction { tx ->
                tx.execute(
                    sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                    parameters = listOf("9", null, "nullname@example.com")
                )
                tx.execute(
                    sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                    parameters = listOf("10", "Null Email User", null)
                )
                tx.execute(
                    sql = "INSERT INTO users (id, name, email) VALUES (?, ?, ?)",
                    parameters = listOf("11", null, null)
                )
            }

            val users = database.getAll(
                sql = "SELECT id, name, email FROM users WHERE id IN (?, ?, ?) order by CAST(id as decimal)",
                parameters = listOf("9", "10", "11")
            ) { cursor ->
                listOf(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2)
                )
            }

            assertEquals(users.size, 3)
            assertEquals(users[0][0], "9")
            assertEquals(users[0][1], null)
            assertEquals(users[0][2], "nullname@example.com")
            assertEquals(users[1][0], "10")
            assertEquals(users[1][1], "Null Email User")
            assertEquals(users[1][2], null)
            assertEquals(users[2][0], "11")
            assertEquals(users[2][1], null)
            assertEquals(users[2][2], null)
        }
    }
}

expect val factory: DatabaseDriverFactory