@file:OptIn(LegacySyncImplementation::class)

package com.powersync.testutils

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.DatabaseDriverFactory
import com.powersync.TestConnector
import com.powersync.bucket.WriteCheckpointData
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.createPowerSyncDatabaseImpl
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Schema
import com.powersync.sync.LegacySyncImplementation
import com.powersync.sync.SyncLine
import com.powersync.utils.JsonUtil
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.toByteArray
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonElement

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

internal fun databaseTest(
    createInitialDatabase: Boolean = true,
    testBody: suspend ActiveDatabaseTest.() -> Unit,
) = runTest {
    val running = ActiveDatabaseTest(this)
    if (createInitialDatabase) {
        // Make sure the database is initialized, we're using internal APIs that expect initialization.
        running.database = running.openDatabaseAndInitialize()
    }

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
                minSeverity = Severity.Verbose,
                logWriterList = listOf(logWriter, generatePrintLogWriter()),
            ),
        )

    @OptIn(LegacySyncImplementation::class)
    var syncLines = Channel<SyncLine>()
    var requestedSyncStreams = mutableListOf<JsonElement>()
    var checkpointResponse: () -> WriteCheckpointResponse = {
        WriteCheckpointResponse(WriteCheckpointData("1000"))
    }

    val testDirectory by lazy { getTempDir() }
    val databaseName by lazy {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val suffix = CharArray(8) { allowedChars.random() }.concatToString()

        "db-$suffix"
    }

    var connector = TestConnector()

    fun openDatabase(schema: Schema = Schema(UserRow.table)): PowerSyncDatabaseImpl {
        logger.d { "Opening database $databaseName in directory $testDirectory" }
        val db =
            createPowerSyncDatabaseImpl(
                factory = factory,
                schema = schema,
                dbFilename = databaseName,
                dbDirectory = testDirectory,
                logger = logger,
                scope = scope,
                createClient = ::createClient,
            )
        doOnCleanup { db.close() }
        return db
    }

    suspend fun openDatabaseAndInitialize(): PowerSyncDatabaseImpl = openDatabase().also { it.readLock { } }

    private fun createClient(config: HttpClientConfig<*>.() -> Unit): HttpClient {
        val engine =
            MockSyncService(
                lines = syncLines,
                generateCheckpoint = { checkpointResponse() },
                trackSyncRequest = {
                    val parsed = JsonUtil.json.parseToJsonElement(it.body.toByteArray().decodeToString())
                    requestedSyncStreams.add(parsed)
                },
            )

        return HttpClient(engine) {
            config()
        }
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

        val path = Path(testDirectory, databaseName).name
        cleanup(path)
    }
}
