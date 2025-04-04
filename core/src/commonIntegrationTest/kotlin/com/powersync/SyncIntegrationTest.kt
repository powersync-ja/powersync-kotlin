package com.powersync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketPriority
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.OpType
import com.powersync.bucket.OplogEntry
import com.powersync.bucket.WriteCheckpointData
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Schema
import com.powersync.sync.SyncLine
import com.powersync.sync.SyncStream
import com.powersync.testutils.MockSyncService
import com.powersync.testutils.UserRow
import com.powersync.testutils.cleanup
import com.powersync.testutils.factory
import com.powersync.testutils.generatePrintLogWriter
import com.powersync.testutils.waitFor
import com.powersync.utils.JsonUtil
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKermitApi::class)
class SyncIntegrationTest {
    private val logWriter =
        TestLogWriter(
            loggable = Severity.Debug,
        )

    private val logger =
        Logger(
            TestConfig(
                minSeverity = Severity.Debug,
                logWriterList = listOf(logWriter, generatePrintLogWriter()),
            ),
        )
    private lateinit var database: PowerSyncDatabaseImpl
    private lateinit var connector: PowerSyncBackendConnector
    private lateinit var syncLines: Channel<SyncLine>
    private lateinit var checkpointResponse: () -> WriteCheckpointResponse

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
        checkpointResponse = { WriteCheckpointResponse(WriteCheckpointData("1000")) }

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

    private fun openDb() =
        PowerSyncDatabase(
            factory = factory,
            schema = Schema(UserRow.table),
            dbFilename = "testdb",
        ) as PowerSyncDatabaseImpl

    @OptIn(DelicateCoroutinesApi::class)
    private fun CoroutineScope.syncStream(): SyncStream {
        val client = MockSyncService(syncLines, { checkpointResponse() })
        return SyncStream(
            bucketStorage = database.bucketStorage,
            connector = connector,
            httpEngine = client,
            uploadCrud = { connector.uploadData(database) },
            retryDelayMs = 10,
            logger = logger,
            params = JsonObject(emptyMap()),
            scope = this,
        )
    }

    private suspend fun expectUserCount(amount: Int) {
        val users = database.getAll("SELECT * FROM users;") { UserRow.from(it) }
        assertEquals(amount, users.size, "Expected $amount users, got $users")
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun closesResponseStreamOnDatabaseClose() =
        runTest {
            val syncStream = syncStream()
            database.connectInternal(syncStream, 1000L)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }

                database.close()
                turbine.waitFor { !it.connected }
                turbine.cancel()
            }

            // Closing the database should have closed the channel
            assertTrue { syncLines.isClosedForSend }
        }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun cleansResourcesOnDisconnect() =
        runTest {
            val syncStream = syncStream()
            database.connectInternal(syncStream, 1000L)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }

                database.disconnect()
                turbine.waitFor { !it.connected }
                turbine.cancel()
            }

            // Disconnecting should have closed the channel
            assertTrue { syncLines.isClosedForSend }

            // And called invalidateCredentials on the connector
            verify { connector.invalidateCredentials() }
        }

    @Test
    fun cannotUpdateSchemaWhileConnected() =
        runTest {
            val syncStream = syncStream()
            database.connectInternal(syncStream, 1000L)

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
        runTest {
            val syncStream = syncStream()
            database.connectInternal(syncStream, 1000L)

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
                val turbine = syncStream.status.asFlow().testIn(this)
                turbine.waitFor { it.connected }
                expectUserCount(0)

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
                    expectUserCount(priorityNo + 1)
                }

                // Then complete the sync
                pushData(3)
                syncLines.send(
                    SyncLine.CheckpointComplete(
                        lastOpId = operationId.toString(),
                    ),
                )
                turbine.waitFor { it.hasSynced == true }
                expectUserCount(4)

                turbine.cancel()
            }

            syncLines.close()
        }

    @Test
    fun testRemembersLastPartialSync() =
        runTest {
            val syncStream = syncStream()
            database.connectInternal(syncStream, 1000L)

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
            database = openDb()
            assertFalse { database.currentStatus.hasSynced == true }
            assertTrue { database.currentStatus.statusForPriority(BucketPriority(1)).hasSynced == true }
            database.close()
            syncLines.close()
        }

    @Test
    fun setsDownloadingState() =
        runTest {
            val syncStream = syncStream()
            database.connectInternal(syncStream, 1000L)

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

            database.close()
            syncLines.close()
        }

    @Test
    fun setsConnectingState() =
        runTest {
            turbineScope(timeout = 10.0.seconds) {
                val syncStream = syncStream()
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connectInternal(syncStream, 1000L)
                turbine.waitFor { it.connecting }

                database.disconnect()

                turbine.waitFor { !it.connecting && !it.connected }
                turbine.cancel()
            }

            database.close()
            syncLines.close()
        }

    @Test
    fun testMultipleSyncsDoNotCreateMultipleStatusEntries() =
        runTest {
            val syncStream = syncStream()
            database.connectInternal(syncStream, 1000L)

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

            database.close()
            syncLines.close()
        }

    @Test
    fun warnsMultipleConnectionAttempts() =
        runTest {
            val db2 =
                PowerSyncDatabase(
                    factory = factory,
                    schema = Schema(UserRow.table),
                    dbFilename = "testdb",
                    logger = logger,
                ) as PowerSyncDatabaseImpl

            turbineScope(timeout = 10.0.seconds) {
                // Connect the first database
                database.connect(connector, 1000L)
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

            db2.close()
            database.close()
            syncLines.close()
        }

    @Test
    fun queuesMultipleConnectionAttempts() =
        runTest {
            val db2 =
                PowerSyncDatabase(
                    factory = factory,
                    schema = Schema(UserRow.table),
                    dbFilename = "testdb",
                    logger = Logger,
                ) as PowerSyncDatabaseImpl

            turbineScope(timeout = 10.0.seconds) {
                val turbine1 = database.currentStatus.asFlow().testIn(this)
                val turbine2 = db2.currentStatus.asFlow().testIn(this)

                // Connect the first database
                database.connect(connector, 1000L)

                turbine1.waitFor { it.connecting }
                db2.connect(connector)

                // Should not be connecting yet
                assertEquals(false, db2.currentStatus.connecting)

                database.disconnect()
                turbine1.waitFor { !it.connecting }

                // Should start connecting after the other database disconnected
                turbine2.waitFor { it.connecting }
                db2.disconnect()
                turbine2.waitFor { !it.connecting }

                turbine1.cancel()
                turbine2.cancel()
            }

            db2.close()
            database.close()
            syncLines.close()
        }

    @Test
    fun reconnectsAfterDisconnecting() =
        runTest {
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

            database.close()
            syncLines.close()
        }

    @Test
    fun reconnects() =
        runTest {
            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector, 1000L, retryDelayMs = 5000)
                turbine.waitFor { it.connecting }

                database.connect(connector, 1000L, retryDelayMs = 5000)
                turbine.waitFor { it.connecting }

                turbine.cancel()
            }

            database.close()
            syncLines.close()
        }

    @Test
    fun `handles checkpoints during uploads`() =
        runTest {
            val testConnector = TestConnector()
            connector = testConnector
            database.connectInternal(syncStream(), 1000L)

            suspend fun expectUserRows(amount: Int) {
                val row = database.get("SELECT COUNT(*) FROM users") { it.getLong(0)!! }
                assertEquals(amount, row.toInt())
            }

            val completeUpload = CompletableDeferred<Unit>()
            val uploadStarted = CompletableDeferred<Unit>()
            testConnector.uploadDataCallback = { db ->
                println("upload data callback called")
                db.getCrudBatch()?.let { batch ->
                    uploadStarted.complete(Unit)
                    completeUpload.await()
                    batch.complete.invoke(null)
                }
            }

            // Trigger an upload (adding a keep-alive sync line because the execute could start before the database is fully
            // connected).
            database.execute("INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)", listOf("local", "local@example.org"))
            syncLines.send(SyncLine.KeepAlive(1234))
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
                turbine.cancel()
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
            expectUserCount(1)

            // Mark the upload as completed, this should trigger a write_checkpoint.json request
            val requestedCheckpoint = CompletableDeferred<Unit>()
            checkpointResponse = {
                requestedCheckpoint.complete(Unit)
                WriteCheckpointResponse(WriteCheckpointData(""))
            }
            println("marking update as completed")
            completeUpload.complete(Unit)
            requestedCheckpoint.await()

            // This should apply the checkpoint
            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { !it.downloading }
                turbine.cancel()
            }

            // Meaning that the two rows are now visible
            expectUserCount(2)
        }
}
