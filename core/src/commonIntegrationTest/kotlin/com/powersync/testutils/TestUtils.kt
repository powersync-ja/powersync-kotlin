package com.powersync.testutils

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Schema
import com.powersync.sync.SyncLine
import com.powersync.sync.SyncStream
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonObject

expect val factory: DatabaseDriverFactory

expect fun cleanup(path: String)

expect fun getTempDir(): String

expect fun isIOS(): Boolean

fun generatePrintLogWriter() =
    object : LogWriter() {
        override fun log(
            severity: Severity,
            message: String,
            tag: String,
            throwable: Throwable?,
        ) {
            println("[$severity:$tag] - $message")
        }
    }

internal fun databaseTest(testBody: suspend ActiveDatabaseTest.() -> Unit) =
    runTest {
        val running = ActiveDatabaseTest(this)
        // Make sure the database is initialized, we're using internal APIs that expect initialization.
        running.database = running.openDatabaseAndInitialize()

        try {
            running.testBody()
        } finally {
            running.cleanup()
        }
    }

@OptIn(ExperimentalKermitApi::class)
internal class ActiveDatabaseTest(
    val scope: TestScope,
) {
    private val cleanupItems: MutableList<suspend () -> Unit> = mutableListOf()

    lateinit var database: PowerSyncDatabaseImpl

    val logWriter =
        TestLogWriter(
            loggable = Severity.Debug,
        )
    val logger =
        Logger(
            TestConfig(
                minSeverity = Severity.Debug,
                logWriterList = listOf(logWriter, generatePrintLogWriter()),
            ),
        )

    val syncLines = Channel<SyncLine>()

    val testDirectory by lazy { getTempDir() }
    val databaseName by lazy {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val suffix = CharArray(8) { allowedChars.random() }.concatToString()

        "db-$suffix"
    }

    val connector =
        mock<PowerSyncBackendConnector> {
            everySuspend { getCredentialsCached() } returns
                PowerSyncCredentials(
                    token = "test-token",
                    userId = "test-user",
                    endpoint = "https://test.com",
                )

            everySuspend { invalidateCredentials() } returns Unit
        }

    fun openDatabase(): PowerSyncDatabaseImpl {
        logger.d { "Opening database $databaseName in directory $testDirectory" }
        val db =
            PowerSyncDatabase(
                factory = factory,
                schema = Schema(UserRow.table),
                dbFilename = databaseName,
                dbDirectory = testDirectory,
                logger = logger,
                scope = scope,
            )
        doOnCleanup { db.close() }
        return db as PowerSyncDatabaseImpl
    }

    suspend fun openDatabaseAndInitialize(): PowerSyncDatabaseImpl = openDatabase().also { it.readLock { } }

    fun syncStream(): SyncStream {
        val client = MockSyncService(syncLines)
        return SyncStream(
            bucketStorage = database.bucketStorage,
            connector = connector,
            httpEngine = client,
            uploadCrud = { },
            retryDelayMs = 10,
            logger = logger,
            params = JsonObject(emptyMap()),
        )
    }

    fun doOnCleanup(action: suspend () -> Unit) {
        cleanupItems += action
    }

    suspend fun cleanup() {
        // Execute in reverse order
        cleanupItems.reverse()
        for (item in cleanupItems) {
            item()
        }

        var path = databaseName
        testDirectory?.let {
            path = Path(it, path).name
        }
        cleanup(path)
    }
}
