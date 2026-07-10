package com.powersync.web

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
                stmt.getDouble(3) shouldBe 1.23

                stmt.step() shouldBe false
            }
        }
        db.close()
    }
}
