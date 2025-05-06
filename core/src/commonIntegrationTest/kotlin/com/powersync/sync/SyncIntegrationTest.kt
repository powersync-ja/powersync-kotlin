package com.powersync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketPriority
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.OpType
import com.powersync.bucket.OplogEntry
import com.powersync.bucket.WriteCheckpointData
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Schema
import com.powersync.sync.SyncLine
import com.powersync.testutils.UserRow
import com.powersync.testutils.databaseTest
import com.powersync.testutils.waitFor
import com.powersync.utils.JsonUtil
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.verify
import dev.mokkery.verifyNoMoreCalls
import dev.mokkery.verifySuspend
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class SyncIntegrationTest {
    private suspend fun PowerSyncDatabase.expectUserCount(amount: Int) {
        val users = getAll("SELECT * FROM users;") { UserRow.from(it) }
        users shouldHaveSize amount
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun connectImmediately() =
        databaseTest(createInitialDatabase = false) {
            // Regression test for https://github.com/powersync-ja/powersync-kotlin/issues/169
            val database = openDatabase()
            database.connect(connector)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }
                turbine.cancel()
            }
        }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun closesResponseStreamOnDatabaseClose() =
        databaseTest {
            database.connect(connector)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }

                database.close()
                turbine.waitFor { !it.connected }
                turbine.cancel()
            }

            // Closing the database should have closed the channel.
            waitFor { syncLines.isClosedForSend shouldBe true }
        }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun cleansResourcesOnDisconnect() =
        databaseTest {
            database.connect(connector)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }

                database.disconnect()
                turbine.waitFor { !it.connected }
                turbine.cancel()
            }

            // Disconnecting should have closed the channel
            waitFor { syncLines.isClosedForSend shouldBe true }

            // And called invalidateCredentials on the connector
            verify { connector.invalidateCredentials() }
        }

    @Test
    fun cannotUpdateSchemaWhileConnected() =
        databaseTest {
            database.connect(connector)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }
                turbine.cancel()
            }

            assertFailsWith<PowerSyncException>("Cannot update schema while connected") {
                database.updateSchema(Schema())
            }

            database.close()
        }

    @Test
    fun testPartialSync() =
        databaseTest {
            database.connect(connector)

            val checksums =
                buildList {
                    for (prio in 0..3) {
                        add(
                            BucketChecksum(
                                bucket = "bucket$prio",
                                priority = BucketPriority(prio),
                                checksum = 10 + prio,
                            ),
                        )
                    }
                }
            var operationId = 1

            suspend fun pushData(priority: Int) {
                val id = operationId++

                syncLines.send(
                    SyncLine.SyncDataBucket(
                        bucket = "bucket$priority",
                        data =
                            listOf(
                                OplogEntry(
                                    checksum = (priority + 10).toLong(),
                                    data =
                                        JsonUtil.json.encodeToString(
                                            mapOf(
                                                "name" to "user $priority",
                                                "email" to "$priority@example.org",
                                            ),
                                        ),
                                    op = OpType.PUT,
                                    opId = id.toString(),
                                    rowId = "prio$priority",
                                    rowType = "users",
                                ),
                            ),
                        after = null,
                        nextAfter = null,
                    ),
                )
            }

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }
                database.expectUserCount(0)

                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "4",
                            checksums = checksums,
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

                    turbine.waitFor { it.statusForPriority(priority).hasSynced == true }
                    database.expectUserCount(priorityNo + 1)
                }

                // Then complete the sync
                pushData(3)
                syncLines.send(
                    SyncLine.CheckpointComplete(
                        lastOpId = operationId.toString(),
                    ),
                )
                turbine.waitFor { it.hasSynced == true }
                database.expectUserCount(4)

                turbine.cancel()
            }
        }

    @Test
    fun testRemembersLastPartialSync() =
        databaseTest {
            database.connect(connector)

            syncLines.send(
                SyncLine.FullCheckpoint(
                    Checkpoint(
                        lastOpId = "4",
                        checksums =
                            listOf(
                                BucketChecksum(
                                    bucket = "bkt",
                                    priority = BucketPriority(1),
                                    checksum = 0,
                                ),
                            ),
                    ),
                ),
            )
            syncLines.send(
                SyncLine.CheckpointPartiallyComplete(
                    lastOpId = "0",
                    priority = BucketPriority(1),
                ),
            )

            database.waitForFirstSync(BucketPriority(1))
            database.close()

            // Connect to the same database again
            database = openDatabaseAndInitialize()
            database.currentStatus.hasSynced shouldBe false
            database.currentStatus.statusForPriority(BucketPriority(1)).hasSynced shouldBe true
        }

    @Test
    fun setsDownloadingState() =
        databaseTest {
            database.connect(connector)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "1",
                            checksums =
                                listOf(
                                    BucketChecksum(
                                        bucket = "bkt",
                                        checksum = 0,
                                    ),
                                ),
                        ),
                    ),
                )
                turbine.waitFor { it.downloading }

                syncLines.send(SyncLine.CheckpointComplete(lastOpId = "1"))
                turbine.waitFor { !it.downloading }
                turbine.cancel()
            }
        }

    @Test
    fun setsConnectingState() =
        databaseTest {
            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector)
                turbine.waitFor { it.connecting }

                database.disconnect()

                turbine.waitFor { !it.connecting && !it.connected }
                turbine.cancel()
            }
        }

    @Test
    fun testMultipleSyncsDoNotCreateMultipleStatusEntries() =
        databaseTest {
            database.connect(connector)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                repeat(5) {
                    syncLines.send(
                        SyncLine.FullCheckpoint(
                            Checkpoint(
                                lastOpId = "1",
                                checksums =
                                    listOf(
                                        BucketChecksum(
                                            bucket = "bkt",
                                            checksum = 0,
                                        ),
                                    ),
                            ),
                        ),
                    )
                    turbine.waitFor { it.downloading }

                    syncLines.send(SyncLine.CheckpointComplete(lastOpId = "1"))
                    turbine.waitFor { !it.downloading }

                    val rows =
                        database.getAll("SELECT * FROM ps_sync_state;") {
                            it.getString(1)!!
                        }

                    assertEquals(1, rows.size)
                }

                turbine.cancel()
            }
        }

    @Test
    @OptIn(ExperimentalKermitApi::class)
    fun warnsMultipleConnectionAttempts() =
        databaseTest {
            val db2 = openDatabaseAndInitialize()

            turbineScope(timeout = 10.0.seconds) {
                // Connect the first database
                database.connect(connector)
                db2.connect(connector)

                waitFor {
                    assertNotNull(
                        logWriter.logs.find {
                            it.message == PowerSyncDatabaseImpl.streamConflictMessage
                        },
                    )
                }

                db2.disconnect()
                database.disconnect()
            }
        }

    @Test
    fun queuesMultipleConnectionAttempts() =
        databaseTest {
            val db2 = openDatabaseAndInitialize()

            turbineScope(timeout = 10.0.seconds) {
                val turbine1 = database.currentStatus.asFlow().testIn(this)
                val turbine2 = db2.currentStatus.asFlow().testIn(this)

                // Connect the first database
                database.connect(connector)

                turbine1.waitFor { it.connecting }
                db2.connect(connector)

                // Should not be connecting yet
                db2.currentStatus.connecting shouldBe false

                database.disconnect()
                turbine1.waitFor { !it.connecting }

                // Should start connecting after the other database disconnected
                turbine2.waitFor { it.connecting }
                db2.disconnect()
                turbine2.waitFor { !it.connecting }

                turbine1.cancel()
                turbine2.cancel()
            }
        }

    @Test
    fun reconnectsAfterDisconnecting() =
        databaseTest {
            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector, 1000L)
                turbine.waitFor { it.connecting }

                database.disconnect()
                turbine.waitFor { !it.connecting }

                database.connect(connector, 1000L)
                turbine.waitFor { it.connecting }
                database.disconnect()
                turbine.waitFor { !it.connecting }

                turbine.cancel()
            }
        }

    @Test
    fun reconnects() =
        databaseTest {
            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector, 1000L, retryDelayMs = 5000)
                turbine.waitFor { it.connecting }

                database.connect(connector, 1000L, retryDelayMs = 5000)
                turbine.waitFor { it.connecting }

                turbine.cancel()
            }
        }

    @Test
    @OptIn(ExperimentalKermitApi::class)
    fun `handles checkpoints during uploads`() =
        databaseTest {
            val testConnector = TestConnector()
            connector = testConnector
            database.connect(testConnector)

            suspend fun expectUserRows(amount: Int) {
                val row = database.get("SELECT COUNT(*) FROM users") { it.getLong(0)!! }
                assertEquals(amount, row.toInt())
            }

            val completeUpload = CompletableDeferred<Unit>()
            val uploadStarted = CompletableDeferred<Unit>()
            testConnector.uploadDataCallback = { db ->
                db.getCrudBatch()?.let { batch ->
                    uploadStarted.complete(Unit)
                    completeUpload.await()
                    batch.complete.invoke(null)
                }
            }

            // Trigger an upload (adding a keep-alive sync line because the execute could start before the database is fully
            // connected).
            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                syncLines.send(SyncLine.KeepAlive(1234))
                turbine.waitFor { it.connected }
                turbine.cancelAndIgnoreRemainingEvents()
            }

            database.execute("INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)", listOf("local", "local@example.org"))

            expectUserRows(1)
            uploadStarted.await()

            // Pretend that the connector takes forever in uploadData, but the data gets uploaded before the method returns.
            syncLines.send(
                SyncLine.FullCheckpoint(
                    Checkpoint(
                        writeCheckpoint = "1",
                        lastOpId = "2",
                        checksums = listOf(BucketChecksum("a", checksum = 0)),
                    ),
                ),
            )
            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.downloading }
                turbine.cancelAndIgnoreRemainingEvents()
            }

            syncLines.send(
                SyncLine.SyncDataBucket(
                    bucket = "a",
                    data =
                        listOf(
                            OplogEntry(
                                checksum = 0,
                                opId = "1",
                                op = OpType.PUT,
                                rowId = "1",
                                rowType = "users",
                                data = """{"id": "test1", "name": "from local", "email": ""}""",
                            ),
                            OplogEntry(
                                checksum = 0,
                                opId = "2",
                                op = OpType.PUT,
                                rowId = "2",
                                rowType = "users",
                                data = """{"id": "test1", "name": "additional entry", "email": ""}""",
                            ),
                        ),
                    after = null,
                    nextAfter = null,
                    hasMore = false,
                ),
            )
            syncLines.send(SyncLine.CheckpointComplete(lastOpId = "2"))

            // Despite receiving a valid checkpoint with two rows, it should not be visible because we have local data.
            waitFor {
                assertNotNull(
                    logWriter.logs.find {
                        it.message.contains("Could not apply checkpoint due to local data")
                    },
                )
            }
            database.expectUserCount(1)

            // Mark the upload as completed, this should trigger a write_checkpoint.json request
            val requestedCheckpoint = CompletableDeferred<Unit>()
            checkpointResponse = {
                requestedCheckpoint.complete(Unit)
                WriteCheckpointResponse(WriteCheckpointData("1"))
            }
            completeUpload.complete(Unit)
            requestedCheckpoint.await()

            // This should apply the checkpoint
            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { !it.downloading }
                turbine.cancelAndIgnoreRemainingEvents()
            }

            // Meaning that the two rows are now visible
            database.expectUserCount(2)
        }

    @Test
    fun testTokenExpired() =
        databaseTest {
            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector, 1000L, retryDelayMs = 5000)
                turbine.waitFor { it.connecting }

                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 4000))
                turbine.waitFor { it.connected }
                verifySuspend { connector.getCredentialsCached() }
                verifyNoMoreCalls(connector)

                // Should invalidate credentials when token expires
                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 0))
                turbine.waitFor { !it.connected }
                verify { connector.invalidateCredentials() }

                turbine.cancel()
            }
        }

    @Test
    fun testTokenPrefetch() =
        databaseTest {
            val prefetchCalled = CompletableDeferred<Unit>()
            val completePrefetch = CompletableDeferred<Unit>()
            every { connector.prefetchCredentials() } returns scope.launch {
                prefetchCalled.complete(Unit)
                completePrefetch.await()
            }

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector, 1000L, retryDelayMs = 5000)
                turbine.waitFor { it.connecting }

                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 4000))
                turbine.waitFor { it.connected }
                verifySuspend { connector.getCredentialsCached() }
                verifyNoMoreCalls(connector)

                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 10))
                prefetchCalled.complete(Unit)
                // Should still be connected before prefetch completes
                database.currentStatus.connected shouldBe true

                // After the prefetch completes, we should reconnect
                completePrefetch.complete(Unit)
                turbine.waitFor { !it.connected }

                turbine.waitFor { it.connected }
                turbine.cancel()
            }
        }
}
