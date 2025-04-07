package com.powersync.testutils

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.CoroutineContext

open class PowerSyncTestFixtures {
    internal fun databaseTest(
        testBody: suspend CurrentTest.() -> Unit
    ) = runTest {
        val running = CurrentTest(this)
        // Make sure the database is initialized, we're using internal APIs that expect initialization.
        running.database = running.openDatabaseAndInitialize()

        withContext(running) {
            running.testBody()
        }

        running.cleanup()
    }

    internal suspend fun currentTest(): CurrentTest {
        return currentCoroutineContext()[CurrentTestKey] ?: error("Not in a running database test")
    }

    @OptIn(ExperimentalKermitApi::class)
    suspend fun logWriter(): TestLogWriter = currentTest().logWriter

    @OptIn(ExperimentalKermitApi::class)
    internal class CurrentTest(val scope: TestScope) : CoroutineContext.Element {
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

        val connector = mock<PowerSyncBackendConnector> {
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
            val db = PowerSyncDatabase(
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

        suspend fun openDatabaseAndInitialize(): PowerSyncDatabaseImpl {
            return openDatabase().also { it.readLock {  } }
        }

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
            for (item in cleanupItems) {
                item()
            }

            var path = databaseName
            testDirectory?.let {
                path = Path(it, path).name
            }
            cleanup(path)
        }

        override val key: CoroutineContext.Key<*>
            get() = CurrentTestKey
    }

    private object CurrentTestKey : CoroutineContext.Key<CurrentTest>
}