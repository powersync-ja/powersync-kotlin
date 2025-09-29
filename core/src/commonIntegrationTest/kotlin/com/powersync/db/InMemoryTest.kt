package com.powersync.db

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.PowerSyncDatabase
import com.powersync.db.schema.Column
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalKermitApi::class)
class InMemoryTest {
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

    @Test
    fun createsSchema() =
        runTest {
            val db = PowerSyncDatabase.Companion.inMemory(schema, this, logger)
            try {
                db.getAll("SELECT * FROM users") { } shouldHaveSize 0
            } finally {
                db.close()
            }
        }

    @Test
    fun watch() =
        runTest {
            val db = PowerSyncDatabase.Companion.inMemory(schema, this, logger)
            try {
                turbineScope {
                    val turbine =
                        db.watch("SELECT name FROM users", mapper = { it.getString(0)!! }).testIn(this)

                    turbine.awaitItem() shouldBe listOf()

                    db.execute("INSERT INTO users (id, name) VALUES (uuid(), ?)", listOf("test user"))
                    turbine.awaitItem() shouldBe listOf("test user")
                    turbine.cancelAndIgnoreRemainingEvents()
                }
            } finally {
                db.close()
            }
        }

    companion object {
        private val schema =
            Schema(
                Table(
                    name = "users",
                    columns =
                        listOf(
                            Column.Companion.text("name"),
                        ),
                ),
            )
    }
}