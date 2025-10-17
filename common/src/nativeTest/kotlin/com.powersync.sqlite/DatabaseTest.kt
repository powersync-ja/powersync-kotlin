package com.powersync.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.powersync.PowerSyncException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DatabaseTest {
    @Test
    fun testInTransaction() =
        inMemoryDatabase().use {
            it.inTransaction() shouldBe false
            it.execSQL("BEGIN")
            it.inTransaction() shouldBe true
            it.execSQL("COMMIT")
            it.inTransaction() shouldBe false

            Unit
        }

    @Test
    fun syntaxError() =
        inMemoryDatabase().use {
            val exception = shouldThrow<PowerSyncException> { it.execSQL("bad syntax") }

            exception.message shouldBe "SqliteException(1): SQL logic error at offset 0, near \"bad\": syntax error for SQL: bad syntax"
            Unit
        }

    private companion object {
        private fun inMemoryDatabase(): SQLiteConnection = Database.open(":memory:", 2)
    }
}
