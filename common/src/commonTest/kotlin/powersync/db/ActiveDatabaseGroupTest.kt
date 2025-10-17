package powersync.db

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.db.ActiveDatabaseGroup
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

    private lateinit var collection: ActiveDatabaseGroup.GroupsCollection

    @BeforeTest
    fun setup() {
        collection = ActiveDatabaseGroup.GroupsCollection()
        logWriter.reset()
    }

    @Test
    fun testTrackDatabase() {
        val usage = collection.referenceDatabase(logger, "test")
        assertEquals(1, collection.allGroups.size)

        usage.first.dispose()
        assertEquals(0, collection.allGroups.size)
    }

    @Test
    fun testWarnsOnDuplicate() {
        val usage = collection.referenceDatabase(logger, "test")
        assertEquals(1, collection.allGroups.size)

        assertEquals(0, logWriter.logs.size)

        val another = collection.referenceDatabase(logger, "test")
        assertNotNull(
            logWriter.logs.find {
                it.message == ActiveDatabaseGroup.Companion.multipleInstancesMessage
            },
        )

        assertEquals(usage.first.group, another.first.group)

        usage.first.dispose()
        assertEquals(1, collection.allGroups.size)
        another.first.dispose()
        assertEquals(0, collection.allGroups.size)
    }

    @Test
    fun testDoesNotWarnForDifferentIdentifiers() {
        val usage = collection.referenceDatabase(logger, "test")
        assertEquals(1, collection.allGroups.size)
        val another = collection.referenceDatabase(logger, "test2")
        assertEquals(2, collection.allGroups.size)

        assertEquals(0, logWriter.logs.size)

        usage.first.dispose()
        assertEquals(1, collection.allGroups.size)
        another.first.dispose()
        assertEquals(0, collection.allGroups.size)
    }
}
