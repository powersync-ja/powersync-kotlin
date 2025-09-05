package com.powersync.sqlite

import androidx.sqlite.SQLiteConnection
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class StatementTest {
    @Test
    fun testBind() =
        inMemoryDatabase().use { db ->
            db.prepare("SELECT json_array(?, ?, ?, ?, hex(?))").use { stmt ->
                stmt.bindDouble(1, 3.14)
                stmt.bindLong(2, 42)
                stmt.bindText(3, "foo")
                stmt.bindNull(4)
                stmt.bindBlob(5, byteArrayOf(1, 2, 3))

                stmt.step() shouldBe true
                stmt.getColumnType(0) shouldBe Statement.SQLITE_TEXT
                stmt.getText(0) shouldBe "[3.14,42,\"foo\",null,\"010203\"]"
            }

            Unit
        }

    @Test
    fun testBindOutOfBounds() =
        inMemoryDatabase().use { db ->
            db.prepare("SELECT ?").use { stmt ->
                shouldThrow<SqliteException> {
                    stmt.bindText(-1, "foo")
                }
                shouldThrow<SqliteException> {
                    stmt.bindText(0, "foo")
                }

                stmt.bindText(1, "foo")

                shouldThrow<SqliteException> {
                    stmt.bindText(2, "foo")
                }
            }
            Unit
        }

    @Test
    fun getBlob() =
        inMemoryDatabase().use { db ->
            db.prepare("SELECT unhex('010203')").use { stmt ->
                stmt.step() shouldBe true
                stmt.getColumnType(0) shouldBe Statement.SQLITE_BLOB
                stmt.getBlob(0) shouldBe byteArrayOf(1, 2, 3)
            }
            Unit
        }

    @Test
    fun getDouble() =
        inMemoryDatabase().use { db ->
            db.prepare("SELECT 3.14").use { stmt ->
                stmt.step() shouldBe true
                stmt.getColumnType(0) shouldBe Statement.SQLITE_FLOAT
                stmt.getDouble(0) shouldBe 3.14
            }
            Unit
        }

    @Test
    fun getLong() =
        inMemoryDatabase().use { db ->
            db.prepare("SELECT 123").use { stmt ->
                stmt.step() shouldBe true
                stmt.getColumnType(0) shouldBe Statement.SQLITE_INTEGER
                stmt.getLong(0) shouldBe 123L
                stmt.getInt(0) shouldBe 123
            }
            Unit
        }

    @Test
    fun getText() =
        inMemoryDatabase().use { db ->
            db.prepare("SELECT 'hello kotlin'").use { stmt ->
                stmt.step() shouldBe true
                stmt.getColumnType(0) shouldBe Statement.SQLITE_TEXT
                stmt.getText(0) shouldBe "hello kotlin"
            }
            Unit
        }

    @Test
    fun getNull() =
        inMemoryDatabase().use { db ->
            db.prepare("SELECT null").use { stmt ->
                stmt.step() shouldBe true
                stmt.isNull(0) shouldBe true
            }
            Unit
        }

    @Test
    fun getOutOfBound() =
        inMemoryDatabase().use { db ->
            db.prepare("SELECT null").use { stmt ->
                stmt.step() shouldBe true

                shouldThrow<IllegalArgumentException> {
                    stmt.getInt(-1)
                }

                shouldThrow<IllegalArgumentException> {
                    stmt.getInt(1)
                }
            }
            Unit
        }

    private companion object {
        private fun inMemoryDatabase(): SQLiteConnection = Database.open(":memory:", 2)
    }
}
