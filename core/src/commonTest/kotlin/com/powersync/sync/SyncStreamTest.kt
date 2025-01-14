package com.powersync.sync

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.bucket.BucketStorage
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.UpdateType
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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

    @BeforeTest
    fun setup() {
        bucketStorage = mock<BucketStorage>()
        connector = mock<PowerSyncBackendConnector>()
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
                    uploadCrud = {},
                    logger = logger,
                    params = JsonObject(emptyMap()),
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
                    uploadCrud = { },
                    retryDelayMs = 10,
                    logger = logger,
                    params = JsonObject(emptyMap()),
                )

            syncStream.status.update(connected = true)
            syncStream.triggerCrudUpload()

            testLogWriter.assertCount(2)

            with(testLogWriter.logs[0]) {
                assertContains(
                    message,
                    "Potentially previously uploaded CRUD entries are still present in the upload queue.",
                )
                assertEquals(Severity.Warn, severity)
            }

            with(testLogWriter.logs[1]) {
                assertEquals(message, "Error uploading crud")
                assertEquals(Severity.Error, severity)
            }
        }

    @Test
    fun testStreamingSyncBasicFlow() =
        runTest {
            bucketStorage =
                mock<BucketStorage> {
                    everySuspend { getClientId() } returns "test-client-id"
                    everySuspend { getBucketStates() } returns emptyList()
                }

            connector =
                mock<PowerSyncBackendConnector> {
                    everySuspend { getCredentialsCached() } returns
                        PowerSyncCredentials(
                            token = "test-token",
                            userId = "test-user",
                            endpoint = "https://test.com",
                        )
                }

            syncStream =
                SyncStream(
                    bucketStorage = bucketStorage,
                    connector = connector,
                    uploadCrud = { },
                    retryDelayMs = 10,
                    logger = logger,
                    params = JsonObject(emptyMap()),
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

    @Test
    fun testStreamingSyncHandlesCancellation() =
        runTest {
            // Setup mocks
            bucketStorage =
                mock<BucketStorage> {
                    everySuspend { getClientId() } returns "test-client-id"
                    everySuspend { getBucketStates() } throws CancellationException("Test cancellation")
                }

            connector =
                mock<PowerSyncBackendConnector> {
                    everySuspend { getCredentialsCached() } returns null
                    everySuspend { invalidateCredentials() } returns Unit
                }

            syncStream =
                SyncStream(
                    bucketStorage = bucketStorage,
                    connector = connector,
                    uploadCrud = { },
                    retryDelayMs = 10,
                    logger = logger,
                    params = JsonObject(emptyMap()),
                )

            val job =
                launch {
                    syncStream.streamingSync()
                }

            delay(100) // Give time for error handling

            // Verify no error was logged for CancellationException
            assertEquals(0, testLogWriter.logs.count { it.severity == Severity.Error })

            // Verify credentials were invalidated
            verify(VerifyMode.exactly(1)) { syncStream.invalidateCredentials() }

            job.cancel()
        }

    @Test
    fun testStreamingSyncHandlesIOException() =
        runTest {
            bucketStorage =
                mock<BucketStorage> {
                    everySuspend { getClientId() } returns "test-client-id"
                    everySuspend { getBucketStates() } throws IOException("Test IO error")
                }

            connector =
                mock<PowerSyncBackendConnector> {
                    everySuspend { getCredentialsCached() } returns null
                    everySuspend { invalidateCredentials() } returns Unit
                }

            syncStream =
                SyncStream(
                    bucketStorage = bucketStorage,
                    connector = connector,
                    uploadCrud = { },
                    retryDelayMs = 10,
                    logger = logger,
                    params = JsonObject(emptyMap()),
                )

            val job =
                launch {
                    syncStream.streamingSync()
                }

            delay(9)

            // Verify error was logged
            assertContains(
                testLogWriter.logs
                    .first {
                        it.severity == Severity.Error
                    }.message,
                "Error in streamingSync: java.io.IOException: Test IO error",
            )

            // Verify credentials weren't invalidated for IOException
            verify(VerifyMode.exactly(0)) { syncStream.invalidateCredentials() }

            job.cancel()
        }

    @Test
    fun testStreamingSyncHandlesOtherException() =
        runTest {
            bucketStorage =
                mock<BucketStorage> {
                    everySuspend { getClientId() } returns "test-client-id"
                    everySuspend { getBucketStates() } throws IllegalStateException("Test error")
                }

            connector =
                mock<PowerSyncBackendConnector> {
                    everySuspend { getCredentialsCached() } returns null
                    everySuspend { invalidateCredentials() } returns Unit
                }

            syncStream =
                SyncStream(
                    bucketStorage = bucketStorage,
                    connector = connector,
                    uploadCrud = { },
                    retryDelayMs = 10,
                    logger = logger,
                    params = JsonObject(emptyMap()),
                )

            val job =
                launch {
                    syncStream.streamingSync()
                }

            delay(9) // Give time for error handling

            // Verify error was logged for non-CancellationException
            assertEquals(1, testLogWriter.logs.count { it.severity == Severity.Error })
            assertContains(testLogWriter.logs.first { it.severity == Severity.Error }.message, "Error in streamingSync")

            // Verify credentials were invalidated for non-IOException
            verify(VerifyMode.exactly(1)) { syncStream.invalidateCredentials() }

            job.cancel()
        }
}
