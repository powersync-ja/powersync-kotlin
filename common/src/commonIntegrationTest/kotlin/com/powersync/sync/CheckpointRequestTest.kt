@file:OptIn(ExperimentalCheckpointRequestsApi::class)

package com.powersync.sync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import com.powersync.ExperimentalCheckpointRequestsApi
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.CustomCheckpointRequestConnector
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.testutils.ActiveDatabaseTest
import com.powersync.testutils.BucketChecksum
import com.powersync.testutils.Checkpoint
import com.powersync.testutils.OpType
import com.powersync.testutils.OplogEntry
import com.powersync.testutils.SyncLine
import com.powersync.testutils.SyncLine.SyncDataBucket
import com.powersync.testutils.UserRow
import com.powersync.testutils.databaseTest
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@ExperimentalKermitApi
class CheckpointRequestTest : AbstractSyncTest() {
    private fun ActiveDatabaseTest.optionsWithRequests() =
        SyncOptions(
            clientConfiguration = SyncClientConfiguration.ExistingClient(createSyncClient()),
            checkpointMode = CheckpointMode.Requests(),
        )

    @Test
    fun `warns for custom connectors without requests being enabled`() =
        databaseTest {
            database.connect(connector.withCustomRequests(), options = getOptions())
            database.waitForStatus { it.connected }
            assertNotNull(
                logWriter.logs.find {
                    it.message.contains("CustomCheckpointRequestConnector was used with legacy checkpoints")
                },
            )
        }

    @Test
    fun `requests checkpoints for updates`() =
        databaseTest {
            database.connect(connector, options = optionsWithRequests())
            database.waitForStatus { it.connected }
            checkpointState.lastCheckpointRequest.value shouldBe 1

            database.execute("INSERT INTO users (id, name, email) VALUES (?, ?, uuid())", listOf("id", "local write"))
            turbineScope {
                val watched = database.watch("SELECT * FROM users") { UserRow.from(it) }.testIn(this)
                watched.awaitItem() shouldHaveSize 1
                // The local write should eventually be uploaded
                checkpointState.lastCheckpointRequest.first { it == 2L }

                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "1",
                            writeCheckpoint = "2",
                            checksums = listOf(BucketChecksum("a", checksum = 0)),
                        ),
                    ),
                )
                syncLines.send(
                    SyncDataBucket(
                        bucket = "a",
                        data =
                            listOf(
                                OplogEntry(
                                    checksum = 0,
                                    opId = "1",
                                    rowId = "id",
                                    rowType = "users",
                                    op = OpType.REMOVE,
                                ),
                            ),
                    ),
                )
                syncLines.send(SyncLine.CheckpointComplete(lastOpId = "1"))
                watched.awaitItem() shouldHaveSize 0
                watched.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reports download error when requesting checkpoint fails`() =
        databaseTest {
            checkpointState.checkpointRequestsSupported = false
            database.connect(connector, options = optionsWithRequests())

            database.waitForStatus { it.downloadError != null }
            database.currentStatus.connected shouldBe false
        }

    @Test
    fun `reposts current checkpoint until applied`() =
        databaseTest {
            database.connect(connector, options = optionsWithRequests())

            // Wait for the initial post (seed)
            checkpointState.checkpointRequestCount.first { it >= 1 }

            for (i in 0 until 10) {
                checkpointState.checkpointRequestCount.first { it >= i + 2 }
            }

            // Finally, include the checkpoint.
            syncLines.send(
                SyncLine.FullCheckpoint(
                    Checkpoint(lastOpId = "0", checksums = emptyList(), writeCheckpoint = "1"),
                ),
            )
            syncLines.send(SyncLine.CheckpointComplete(lastOpId = "0"))
            database.waitForFirstSync()

            val totalRequests = checkpointState.checkpointRequestCount.value

            // Which means we shouldn't keep requesting it.
            delay(1.hours)
            checkpointState.checkpointRequestCount.value shouldBe totalRequests
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    // TODO: Un-ignore this test. It's currently broken due to missing crud uploads.
    @Ignore
    fun `download is retried on checkpoint request`() =
        databaseTest {
            database.connect(connector, retryDelayMs = 10_000, options = optionsWithRequests())

            // Destroy the initial connection by sending a bogus line
            syncLines.send(
                SyncLine.FullCheckpoint(Checkpoint(lastOpId = "invalid line", checksums = emptyList())),
            )
            database.waitForStatus { it.downloadError != null }

            val timeToReconnect =
                scope.testTimeSource.measureTime {
                    // Trigger an upload here. Because the upload needs a seeded sync iteration, we should
                    // reconnect immediately instead of after the configured 10s delay.
                    database.execute(
                        "INSERT INTO users (id, name, email) VALUES (uuid(), ?, uuid())",
                        listOf("restart plz"),
                    )

                    database.currentStatus.asFlow().first { it.connected }
                }
            timeToReconnect shouldBeLessThan 5.seconds
        }

    @Test
    fun `can use checkpoint method from connector`() =
        databaseTest {
            val didRequestCheckpoint = CompletableDeferred<Unit>()
            val customConnector =
                connector.withCustomRequests { _, requestId ->
                    requestId shouldBe 1L
                    didRequestCheckpoint.complete(Unit)
                    requestId
                }

            database.connect(customConnector, options = optionsWithRequests())
            didRequestCheckpoint.await()
        }

    @Test
    fun `reconciles checkpoint state on token expiry`() =
        databaseTest {
            checkpointState.lastCheckpointRequest.value = 100
            database.connect(connector, options = optionsWithRequests())
            checkpointState.checkpointRequestCount.first { it == 1 }
            database.waitForStatus { it.connected }
            scope.testScheduler.runCurrent()

            // Simulate what would happen if we suddenly switched users after the old token expired. The
            // client expects a checkpoint of 100, for another user the service wouldn't have that counter
            // yet. The client must request a checkpoint with the existing id, allowing the service to
            // recognize that this device + user combo needs higher checkpoint ids.
            checkpointState.lastCheckpointRequest.value = 0
            syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 0))
            checkpointState.checkpointRequestCount.first { it == 2 }
            checkpointState.lastCheckpointRequest.value shouldBe 100
        }

    @Test
    fun `reads sync lines before checkpoint requests are ready`() =
        databaseTest {
            val hasInitialRequest = CompletableDeferred<Unit>()
            val completeInitialRequest = CompletableDeferred<Unit>()
            checkpointState.beforeCheckpointRequestResponse = {
                hasInitialRequest.complete(Unit)
                completeInitialRequest.await()
            }

            database.connect(connector, options = optionsWithRequests())
            hasInitialRequest.await()

            syncLines.send(
                SyncLine.FullCheckpoint(
                    Checkpoint(lastOpId = "0", checksums = emptyList(), writeCheckpoint = "1"),
                ),
            )
            database.waitForStatus { it.downloading }
            completeInitialRequest.complete(Unit)
        }
}

private fun PowerSyncBackendConnector.withCustomRequests(
    postRequest: suspend (String, Long) -> Long = { _, req -> req },
): CustomCheckpointRequestConnector =
    object : CustomCheckpointRequestConnector() {
        override suspend fun postCheckpointRequest(
            clientId: String,
            requestId: Long,
        ): Long = postRequest(clientId, requestId)

        override suspend fun fetchCredentials(): PowerSyncCredentials? = this@withCustomRequests.fetchCredentials()

        override suspend fun uploadData(database: PowerSyncDatabase) = this@withCustomRequests.uploadData(database)
    }
