package com.powersync.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DatabaseTest {
    @Test
    fun testInTransaction() = inMemoryDatabase().use {
        it.inTransaction() shouldBe false
        it.execSQL("BEGIN")
        it.inTransaction() shouldBe true
        it.execSQL("COMMIT")
        it.inTransaction() shouldBe false

        Unit
    }

    private companion object {
        private fun inMemoryDatabase(): SQLiteConnection {
            return Database.open(":memory", 0)
        }
    }
}
