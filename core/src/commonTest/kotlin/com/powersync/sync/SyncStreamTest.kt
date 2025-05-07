package com.powersync.sync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketPriority
import com.powersync.bucket.BucketStorage
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.OpType
import com.powersync.bucket.OplogEntry
import com.powersync.bucket.WriteCheckpointData
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.UpdateType
import com.powersync.testutils.MockSyncService
import com.powersync.testutils.waitFor
import com.powersync.utils.JsonUtil
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.resetCalls
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.order
import dev.mokkery.verifyNoMoreCalls
import dev.mokkery.verifySuspend
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(co.touchlab.kermit.ExperimentalKermitApi::class)
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
                    scope = this,
                    options = SyncOptions(),
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
                    scope = this,
                    options = SyncOptions(),
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
                    scope = this,
                    options = SyncOptions()
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
