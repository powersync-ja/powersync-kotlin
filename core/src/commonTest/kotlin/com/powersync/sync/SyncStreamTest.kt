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
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
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
                everySuspend { getBucketStates() } returns emptyList()
                everySuspend { removeBuckets(any()) } returns Unit
                everySuspend { setTargetCheckpoint(any()) } returns Unit
                everySuspend { saveSyncData(any()) } returns Unit
                everySuspend { syncLocalDatabase(any(), any()) } returns
                    SyncLocalDatabaseResult(
                        ready = true,
                        checkpointValid = true,
                        checkpointFailures = emptyList(),
                    )
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
                    httpEngine = assertNoHttpEngine,
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
                    httpEngine = assertNoHttpEngine,
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
                    everySuspend { getBucketStates() } returns emptyList()
                }

            syncStream =
                SyncStream(
                    bucketStorage = bucketStorage,
                    connector = connector,
                    httpEngine = assertNoHttpEngine,
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
    fun testPartialSync() =
        runTest {
            // TODO: It would be neat if we could use in-memory sqlite instances instead of mocking everything
            // Revisit https://github.com/powersync-ja/powersync-kotlin/pull/117/files at some point
            val syncLines = Channel<SyncLine>()
            val client = MockSyncService.client(this, syncLines.receiveAsFlow())

            syncStream =
                SyncStream(
                    bucketStorage = bucketStorage,
                    connector = connector,
                    httpEngine = client,
                    uploadCrud = { },
                    retryDelayMs = 10,
                    logger = logger,
                    params = JsonObject(emptyMap()),
                )

            val job = launch { syncStream.streamingSync() }
            var operationId = 1

            suspend fun pushData(priority: Int) {
                val id = operationId++

                syncLines.send(
                    SyncLine.SyncDataBucket(
                        bucket = "prio$priority",
                        data =
                            listOf(
                                OplogEntry(
                                    checksum = (priority + 10).toLong(),
                                    data = JsonUtil.json.encodeToString(mapOf("foo" to "bar")),
                                    op = OpType.PUT,
                                    opId = id.toString(),
                                    rowId = "prio$priority",
                                    rowType = "customers",
                                ),
                            ),
                        after = null,
                        nextAfter = null,
                    ),
                )
            }

            turbineScope(timeout = 10.0.seconds) {
                val turbine = syncStream.status.asFlow().testIn(this)
                turbine.waitFor { it.connected }
                resetCalls(bucketStorage)

                // Start a sync flow
                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "4",
                            checksums =
                                buildList {
                                    for (priority in 0..3) {
                                        add(
                                            BucketChecksum(
                                                bucket = "prio$priority",
                                                priority = BucketPriority(priority),
                                                checksum = 10 + priority,
                                            ),
                                        )
                                    }
                                },
                        ),
                    ),
                )

                // Emit a partial sync complete for each priority but the last.
                for (priorityNo in 0..<3) {
                    val priority = BucketPriority(priorityNo)
                    pushData(priorityNo)
                    syncLines.send(
                        SyncLine.CheckpointPartiallyComplete(
                            lastOpId = operationId.toString(),
                            priority = priority,
                        ),
                    )

                    turbine.waitFor { it.priorityStatusFor(priority).hasSynced == true }

                    verifySuspend(order) {
                        if (priorityNo == 0) {
                            bucketStorage.removeBuckets(any())
                            bucketStorage.setTargetCheckpoint(any())
                        }

                        bucketStorage.saveSyncData(any())
                        bucketStorage.syncLocalDatabase(any(), priority)
                    }
                }

                // Then complete the sync
                pushData(3)
                syncLines.send(
                    SyncLine.CheckpointComplete(
                        lastOpId = operationId.toString(),
                    ),
                )

                turbine.waitFor { it.hasSynced == true }
                verifySuspend {
                    bucketStorage.saveSyncData(any())
                    bucketStorage.syncLocalDatabase(any(), null)
                }

                turbine.cancel()
            }

            verifyNoMoreCalls(bucketStorage)
            job.cancel()
            syncLines.close()
        }
}
