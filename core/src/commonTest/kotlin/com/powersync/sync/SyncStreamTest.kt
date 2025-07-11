package com.powersync.sync

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.bucket.BucketStorage
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.UpdateType
import com.powersync.db.schema.Schema
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@OptIn(ExperimentalKermitApi::class, ExperimentalPowerSyncAPI::class)
class SyncStreamTest {
    private lateinit var bucketStorage: BucketStorage
    private lateinit var connector: PowerSyncBackendConnector
    private lateinit var syncStream: SyncStream
    private val testLogWriter =
        TestLogWriter(
            loggable = Severity.Verbose,
        )
    private val logger =
        Logger(
            TestConfig(
                minSeverity = Severity.Debug,
                logWriterList = listOf(testLogWriter),
            ),
        )
    private val assertNoHttpEngine =
        MockEngine { request ->
            error("Unexpected HTTP request: $request")
        }

    @BeforeTest
    fun setup() {
        bucketStorage =
            mock<BucketStorage> {
                everySuspend { getClientId() } returns "test-client-id"
            }
        connector =
            mock<PowerSyncBackendConnector> {
                everySuspend { getCredentialsCached() } returns
                    PowerSyncCredentials(
                        token = "test-token",
                        endpoint = "https://test.com",
                    )
            }
    }

    @Test
    fun testInvalidateCredentials() =
        runTest {
            connector =
                mock<PowerSyncBackendConnector> {
                    everySuspend { invalidateCredentials() } returns Unit
                }

            syncStream =
                SyncStream(
                    bucketStorage = bucketStorage,
                    connector = connector,
                    createClient = { config -> HttpClient(assertNoHttpEngine, config) },
                    uploadCrud = {},
                    logger = logger,
                    params = JsonObject(emptyMap()),
                    uploadScope = this,
                    options = SyncOptions(),
                    schema = Schema(),
                )

            syncStream.invalidateCredentials()
            verify { connector.invalidateCredentials() }
        }

    // TODO: Work on improving testing this without needing to test the logs are displayed
    @Test
    fun testTriggerCrudUploadWhenAlreadyUploading() =
        runTest {
            val mockCrudEntry =
                CrudEntry(
                    id = "1",
                    clientId = 1,
                    op = UpdateType.PUT,
                    table = "table1",
                    transactionId = 1,
                    opData =
                        mapOf(
                            "key" to "value",
                        ),
                )
            bucketStorage =
                mock<BucketStorage> {
                    everySuspend { nextCrudItem() } returns mockCrudEntry
                }

            syncStream =
                SyncStream(
                    bucketStorage = bucketStorage,
                    connector = connector,
                    createClient = { config -> HttpClient(assertNoHttpEngine, config) },
                    uploadCrud = { },
                    retryDelayMs = 10,
                    logger = logger,
                    params = JsonObject(emptyMap()),
                    uploadScope = this,
                    options = SyncOptions(),
                    schema = Schema(),
                )

            syncStream.status.update { copy(connected = true) }
            syncStream.triggerCrudUploadAsync().join()

            testLogWriter.assertCount(2)

            with(testLogWriter.logs[0]) {
                assertContains(
                    message,
                    "Potentially previously uploaded CRUD entries are still present in the upload queue.",
                )
                assertEquals(Severity.Warn, severity)
            }

            with(testLogWriter.logs[1]) {
                assertEquals(message, "Error uploading crud: Delaying due to previously encountered CRUD item.")
                assertEquals(Severity.Error, severity)
            }
        }

    @Test
    fun testStreamingSyncBasicFlow() =
        runTest {
            bucketStorage =
                mock<BucketStorage> {
                    everySuspend { getClientId() } returns "test-client-id"
                }

            syncStream =
                SyncStream(
                    bucketStorage = bucketStorage,
                    connector = connector,
                    createClient = { config -> HttpClient(assertNoHttpEngine, config) },
                    uploadCrud = { },
                    retryDelayMs = 10,
                    logger = logger,
                    params = JsonObject(emptyMap()),
                    uploadScope = this,
                    options = SyncOptions(),
                    schema = Schema(),
                )

            // Launch streaming sync in a coroutine that we'll cancel after verification
            val job =
                launch {
                    syncStream.streamingSync()
                }

            // Wait for status to update
            withTimeout(1000) {
                while (!syncStream.status.connecting) {
                    delay(10)
                }
            }

            // Verify initial state
            assertEquals(true, syncStream.status.connecting)
            assertEquals(false, syncStream.status.connected)

            // Clean up
            job.cancel()
        }
}
