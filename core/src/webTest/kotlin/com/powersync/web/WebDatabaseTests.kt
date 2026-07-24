package com.powersync.web

import androidx.sqlite.SQLiteException
import app.cash.turbine.turbineScope
import io.kotest.assertions.failure
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class WebDatabaseTests {
    @Test
    fun canUseJavaScriptSymbols() {
        val x = DatabaseImplementation.opfsShared
        x.access shouldBe "throughSharedWorker"

        println(dartWorkerUri())
        println(sqlite3WasmUri())
    }

    @Test
    fun bindValues() =
        runTest {
            val db = WebConnectionFactory(this).open("bindValues.db", DatabaseImplementation.inMemoryShared)
            db.read { context ->
                context.usePreparedAsync("SELECT typeof(?)") { stmt ->
                    suspend fun expectType(expectedType: String) {
                        stmt.step() shouldBe true
                        stmt.getText(0) shouldBe expectedType
                        stmt.step() shouldBe false
                        stmt.reset()
                    }

                    stmt.bindNull(1)
                    expectType("null")

                    stmt.bindText(1, "Hello from Kotlin")
                    expectType("text")

                    stmt.bindInt(1, 3)
                    expectType("integer")
                    stmt.bindLong(1, 3)
                    expectType("integer")

                    stmt.bindDouble(1, 3.0)
                    expectType("real")

                    stmt.bindBlob(1, byteArrayOf(1, 2, 3))
                    expectType("blob")
                }
            }
            db.close()
        }

    @Test
    fun readValues() =
        runTest {
            val db = WebConnectionFactory(this).open("readValues.db", DatabaseImplementation.inMemoryShared)
            db.read { context ->
                context.usePreparedAsync("SELECT NULL, 'Hello from SQLite', 3, 3.0, x'1234'") { stmt ->
                    stmt.step() shouldBe true

                    stmt.isNull(0) shouldBe true
                    stmt.getColumnType(0) shouldBe 5 // SQLITE_NULL

                    stmt.isNull(1) shouldBe false
                    stmt.getColumnType(1) shouldBe 3 // SQLITE_TEXT
                    stmt.getText(1) shouldBe "Hello from SQLite"

                    stmt.getColumnType(2) shouldBe 1 // SQLITE_INTEGER
                    stmt.getInt(2) shouldBe 3

                    stmt.getColumnType(3) shouldBe 2 // SQLITE_FLOAT
                    stmt.getDouble(3) shouldBe 3.0

                    stmt.getColumnType(4) shouldBe 4 // SQLITE_BLOB
                    stmt.getBlob(4) shouldBe byteArrayOf(0x12, 0x34)

                    stmt.step() shouldBe false
                }
            }
        }

    @Test
    fun tableUpdates() =
        runTest {
            val factory = WebConnectionFactory(this)
            val pool = factory.openPool { open("openPoolUpdates.db", DatabaseImplementation.inMemoryShared) }
            pool.write { it.execSQL("CREATE TABLE users (name TEXT);") }

            turbineScope(timeout = 1.seconds) {
                val updates = pool.updates.testIn(this)
                pool.write { it.execSQL("INSERT INTO users (name) VALUES ('Web user')") }

                updates.awaitItem() shouldBe setOf("users")
                updates.cancelAndIgnoreRemainingEvents()
            }

            pool.close()
        }

    @Test
    fun bindManyParameters() =
        runTest {
            val db = WebConnectionFactory(this).open("manyParameters.db", DatabaseImplementation.inMemoryShared)
            db.read { context ->
                val count = 40
                val placeholders = List(count) { "?" }.joinToString(",")

                context.usePreparedAsync("SELECT $placeholders") { stmt ->
                    for (i in 1..count) {
                        stmt.bindInt(i, i)
                    }

                    stmt.step() shouldBe true
                    for (i in 1..count) {
                        stmt.getInt(i - 1) shouldBe i
                    }
                    stmt.step() shouldBe false
                }
            }
            db.close()
        }

    @Test
    fun throwsSqliteException() =
        runTest {
            val db = WebConnectionFactory(this).open("exceptions.db", DatabaseImplementation.inMemoryShared)
            val exception =
                shouldThrow<SQLiteException> {
                    db.read { it.execSQL("SELECT this is a syntax error") }
                }

            exception.toString() shouldContain "SqliteException(1): while executing, near \"error\": syntax error, SQL logic error (code 1)"
        }

    @Test
    fun forwardsCancellations() =
        runTest {
            val db = WebConnectionFactory(this).open("cancellations.db", DatabaseImplementation.inMemoryShared)

            db.write {
                val job =
                    async {
                        db.write { failure("Should not grant a second write") }
                    }

                job.cancel()
                shouldThrow<CancellationException> { job.await() }
            }
        }
}
