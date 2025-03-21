package com.powersync.db

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalKermitApi::class)
class ActiveDatabaseGroupTest {

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

    @BeforeTest
    fun setupDatabase() {
        logWriter.reset()
    }

    @Test
    fun testTrackDatabase() {
        val usage = ActiveDatabaseGroup.referenceDatabase(logger, "test")
        assertEquals(1, ActiveDatabaseGroup.allGroups.size)

        usage.first.dispose()
        assertEquals(0, ActiveDatabaseGroup.allGroups.size)
    }

    @Test
    fun testWarnsOnDuplicate() {
        val usage = ActiveDatabaseGroup.referenceDatabase(logger, "test")
        assertEquals(1, ActiveDatabaseGroup.allGroups.size)

        assertEquals(0, logWriter.logs.size)

        val another = ActiveDatabaseGroup.referenceDatabase(logger, "test")
        assertNotNull(
            logWriter.logs.find {
                it.message == ActiveDatabaseGroup.multipleInstancesMessage
            },
        )

        assertEquals(usage.first.group, another.first.group)

        usage.first.dispose()
        assertEquals(1, ActiveDatabaseGroup.allGroups.size)
        another.first.dispose()
        assertEquals(0, ActiveDatabaseGroup.allGroups.size)
    }

    @Test
    fun testDoesNotWarnForDifferentIdentifiers() {
        val usage = ActiveDatabaseGroup.referenceDatabase(logger, "test")
        assertEquals(1, ActiveDatabaseGroup.allGroups.size)
        val another = ActiveDatabaseGroup.referenceDatabase(logger, "test2")
        assertEquals(2, ActiveDatabaseGroup.allGroups.size)

        assertEquals(0, logWriter.logs.size)

        usage.first.dispose()
        assertEquals(1, ActiveDatabaseGroup.allGroups.size)
        another.first.dispose()
        assertEquals(0, ActiveDatabaseGroup.allGroups.size)
    }
}
