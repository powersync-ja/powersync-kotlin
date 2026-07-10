package com.powersync.web

import app.cash.turbine.turbineScope
import io.kotest.matchers.shouldBe
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
    fun bindValues() = runTest {
        val db = WebConnectionFactory(this).open("bindValues.db", DatabaseImplementation.inMemoryShared)
        db.read { context ->
            context.usePreparedAsync("SELECT ?, ?, ?, ?") { stmt ->
                stmt.bindNull(1)
                stmt.bindText(2, "Hello from Kotlin")
                stmt.bindInt(3, 123)
                stmt.bindDouble(4, 1.23)

                stmt.step() shouldBe true
                stmt.isNull(0) shouldBe true
                stmt.getText(1) shouldBe "Hello from Kotlin"
                stmt.getInt(2) shouldBe 123
                stmt.getLong(2) shouldBe 123L
                stmt.getDouble(3) shouldBe 1.23

                stmt.step() shouldBe false
            }
        }
        db.close()
    }

    @Test
    fun tableUpdates() = runTest {
        val db = WebConnectionFactory(this).open("tableUpdates.db", DatabaseImplementation.inMemoryShared)
        db.write { it.execSQL("CREATE TABLE users (name TEXT);") }

        turbineScope(timeout = 1.seconds) {
            val updates = db.updates.testIn(this)
            db.write { it.execSQL("INSERT INTO users (name) VALUES ('Web user')") }

            updates.awaitItem() shouldBe setOf("users")
            updates.cancelAndIgnoreRemainingEvents()
        }
    }
}
