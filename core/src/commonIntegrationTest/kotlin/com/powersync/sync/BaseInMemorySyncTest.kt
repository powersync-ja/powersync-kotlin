package com.powersync.sync

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
import com.powersync.testutils.MockSyncService
import com.powersync.testutils.UserRow
import com.powersync.testutils.cleanup
import com.powersync.testutils.factory
import com.powersync.testutils.generatePrintLogWriter
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@OptIn(ExperimentalKermitApi::class)
abstract class BaseInMemorySyncTest {
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
    internal lateinit var database: PowerSyncDatabaseImpl
    internal lateinit var connector: PowerSyncBackendConnector
    internal lateinit var syncLines: Channel<SyncLine>

    @BeforeTest
    fun setup() {
        cleanup("testdb")
        logWriter.reset()
        database = openDb()
        connector =
            mock<PowerSyncBackendConnector> {
                everySuspend { getCredentialsCached() } returns
                        PowerSyncCredentials(
                            token = "test-token",
                            userId = "test-user",
                            endpoint = "https://test.com",
                        )

                everySuspend { invalidateCredentials() } returns Unit
            }
        syncLines = Channel()

        runBlocking {
            database.disconnectAndClear(true)
        }
    }

    @AfterTest
    fun teardown() {
        runBlocking {
            database.close()
        }
        cleanup("testdb")
    }

    internal fun openDb() =
        PowerSyncDatabase(
            factory = factory,
            schema = Schema(UserRow.table),
            dbFilename = "testdb",
        ) as PowerSyncDatabaseImpl

    internal fun syncStream(): SyncStream {
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
}
