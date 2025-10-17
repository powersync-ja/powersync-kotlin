package com.powersync.sync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.test.TestConnector
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.OpType
import com.powersync.bucket.OplogEntry
import com.powersync.bucket.StreamPriority
import com.powersync.bucket.WriteCheckpointData
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.connectors.readCachedCredentials
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.PendingStatement
import com.powersync.db.schema.PendingStatementParameter
import com.powersync.db.schema.RawTable
import com.powersync.db.schema.Schema
import com.powersync.test.waitFor
import com.powersync.testutils.UserRow
import com.powersync.testutils.databaseTest
import com.powersync.testutils.waitFor
import com.powersync.utils.JsonParam
import com.powersync.utils.JsonUtil
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.ContentType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

@OptIn(LegacySyncImplementation::class)
abstract class BaseSyncIntegrationTest(
    useNewSyncImplementation: Boolean,
) : AbstractSyncTest(
        useNewSyncImplementation,
    ) {
    private suspend fun PowerSyncDatabase.expectUserCount(amount: Int) {
        val users = getAll("SELECT * FROM users;") { UserRow.from(it) }
        users shouldHaveSize amount
    }

    @Test
    fun connectImmediately() =
        databaseTest(createInitialDatabase = false) {
            // Regression test for https://github.com/powersync-ja/powersync-kotlin/issues/169
            val database = openDatabase()
            database.connect(connector, options = getOptions())

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }
                turbine.cancel()
            }
        }

    @Test
    fun useParameters() =
        databaseTest {
            database.connect(
                connector,
                options = getOptions(),
                params = mapOf("foo" to JsonParam.String("bar")),
            )
            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }
                turbine.cancel()
            }

            requestedSyncStreams shouldHaveSingleElement {
                val params = it.jsonObject["parameters"]!!.jsonObject
                params.keys shouldHaveSingleElement "foo"
                params.values
                    .first()
                    .jsonPrimitive.content shouldBe "bar"
                true
            }
        }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun closesResponseStreamOnDatabaseClose() =
        databaseTest {
            database.connect(connector, options = getOptions())

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }

                database.close()
                turbine.waitFor { !it.connected }
                turbine.cancelAndIgnoreRemainingEvents()
            }

            // Closing the database should have closed the channel.
            logger.v { "Database is closed, waiting to close HTTP stream" }
            waitFor { syncLines.isClosedForSend shouldBe true }
        }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun cleansResourcesOnDisconnect() =
        databaseTest {
            database.connect(connector, options = getOptions())

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }
                connector.readCachedCredentials() shouldNotBe null

                database.disconnect()
                turbine.waitFor { !it.connected }
                turbine.cancel()
            }

            // Disconnecting should have closed the channel
            waitFor { syncLines.isClosedForSend shouldBe true }

            // And called invalidateCredentials on the connector
            connector.readCachedCredentials() shouldBe null
        }

    @Test
    fun cannotUpdateSchemaWhileConnected() =
        databaseTest {
            database.connect(connector, options = getOptions())

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
            database.connect(connector, options = getOptions())

            val checksums =
                buildList {
                    for (prio in 0..3) {
                        add(
                            BucketChecksum(
                                bucket = "bucket$prio",
                                priority = StreamPriority(prio),
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
                    val priority = StreamPriority(priorityNo)
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
            database.connect(connector, options = getOptions())

            syncLines.send(
                SyncLine.FullCheckpoint(
                    Checkpoint(
                        lastOpId = "4",
                        checksums =
                            listOf(
                                BucketChecksum(
                                    bucket = "bkt",
                                    priority = StreamPriority(1),
                                    checksum = 0,
                                ),
                            ),
                    ),
                ),
            )
            syncLines.send(
                SyncLine.CheckpointPartiallyComplete(
                    lastOpId = "0",
                    priority = StreamPriority(1),
                ),
            )

            database.waitForFirstSync(StreamPriority(1))
            database.close()

            // Connect to the same database again
            database = openDatabaseAndInitialize()
            database.currentStatus.hasSynced shouldBe false
            database.currentStatus.statusForPriority(StreamPriority(1)).hasSynced shouldBe true
        }

    @Test
    fun setsDownloadingState() =
        databaseTest {
            database.connect(connector, options = getOptions())

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

                database.connect(connector, options = getOptions())
                turbine.waitFor { it.connecting }

                database.disconnect()

                turbine.waitFor { !it.connecting && !it.connected }
                turbine.cancel()
            }
        }

    @Test
    fun testMultipleSyncsDoNotCreateMultipleStatusEntries() =
        databaseTest {
            database.connect(connector, options = getOptions())

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
                database.connect(connector, options = getOptions())
                db2.connect(connector, options = getOptions())

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
                database.connect(connector, options = getOptions())

                turbine1.waitFor { it.connecting }
                db2.connect(connector, options = getOptions())

                // Should not be connecting yet
                db2.currentStatus.connecting shouldBe false

                database.disconnect()
                turbine1.waitFor { !it.connecting }

                // Should start connecting after the other database disconnected
                turbine2.waitFor { it.connecting }
                db2.disconnect()
                turbine2.waitFor { !it.connecting }

                turbine1.cancelAndIgnoreRemainingEvents()
                turbine2.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun reconnectsAfterDisconnecting() =
        databaseTest {
            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector, 1000L, options = getOptions())
                turbine.waitFor { it.connecting }

                database.disconnect()
                turbine.waitFor { !it.connecting }

                database.connect(connector, 1000L, options = getOptions())
                turbine.waitFor { it.connecting }
                database.disconnect()
                turbine.waitFor { !it.connecting }

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun reconnects() =
        databaseTest {
            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector, 1000L, retryDelayMs = 5000, options = getOptions())
                turbine.waitFor { it.connecting }

                database.connect(connector, 1000L, retryDelayMs = 5000, options = getOptions())
                turbine.waitFor { it.connecting }

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @OptIn(ExperimentalKermitApi::class)
    fun `handles checkpoints during uploads`() =
        databaseTest {
            val testConnector = TestConnector()
            connector = testConnector
            database.connect(testConnector, options = getOptions())

            suspend fun expectUserRows(amount: Int) {
                val row = database.get("SELECT COUNT(*) FROM users") { it.getLong(0)!! }
                assertEquals(amount, row.toInt())
            }

            val completeUpload = CompletableDeferred<Unit>()
            val uploadStarted = CompletableDeferred<Unit>()
            testConnector.uploadDataCallback = { db ->
                db.getCrudBatch()?.let { batch ->
                    logger.v { "connector: uploading crud batch" }
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

            // Wait for the first upload task triggered when connecting to be complete.
            withContext(Dispatchers.Default) {
                waitFor {
                    assertNotNull(
                        logWriter.logs.find {
                            it.message.contains("crud upload: notify completion")
                        },
                    )
                }
            }

            database.execute(
                "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                listOf("local", "local@example.org"),
            )

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
            logger.d { "Did request checkpoint" }

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
    fun `handles write made while offline`() =
        databaseTest {
            connector = TestConnector()
            val uploadCompleted = CompletableDeferred<Unit>()
            checkpointResponse = {
                uploadCompleted.complete(Unit)
                WriteCheckpointResponse(WriteCheckpointData("1"))
            }

            database.execute(
                "INSERT INTO users (id, name) VALUES (uuid(), ?)",
                listOf("local write"),
            )
            database.connect(connector, options = getOptions())

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(scope)
                turbine.waitFor { it.connected }

                val query =
                    database.watch("SELECT name FROM users") { it.getString(0)!! }.testIn(scope)
                query.awaitItem() shouldBe listOf("local write")

                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            writeCheckpoint = "1",
                            lastOpId = "1",
                            checksums = listOf(BucketChecksum("a", checksum = 0)),
                        ),
                    ),
                )
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
                                    data = """{"id": "test1", "name": "from server"}""",
                                ),
                            ),
                        after = null,
                        nextAfter = null,
                    ),
                )

                uploadCompleted.await()
                syncLines.send(SyncLine.CheckpointComplete("1"))

                query.awaitItem() shouldBe listOf("from server")

                turbine.cancelAndIgnoreRemainingEvents()
                query.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testTokenExpired() =
        databaseTest {
            var fetchCredentialsCalls = 0
            connector.fetchCredentialsCallback = {
                fetchCredentialsCalls++
                TestConnector.testCredentials
            }

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector, 1000L, retryDelayMs = 5000, options = getOptions())
                turbine.waitFor { it.connecting }

                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 4000))
                turbine.waitFor { it.connected }
                fetchCredentialsCalls shouldBe 1

                // Should invalidate credentials when token expires
                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 0))
                turbine.waitFor { !it.connected }
                connector.readCachedCredentials() shouldBe null

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testTokenThrows() =
        databaseTest {
            // Regression test for https://github.com/powersync-ja/powersync-kotlin/issues/219
            var attempt = 0
            val connector =
                object : PowerSyncBackendConnector() {
                    override suspend fun fetchCredentials(): PowerSyncCredentials? {
                        attempt++
                        if (attempt == 1) {
                            fail("Expected exception from fetchCredentials")
                        }

                        return TestConnector.testCredentials
                    }

                    override suspend fun uploadData(database: PowerSyncDatabase) {
                        fail("Not implemented: uploadData")
                    }
                }

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector, 1000L, retryDelayMs = 5000, options = getOptions())
                turbine.waitFor { it.downloadError != null }

                database.currentStatus.downloadError?.toString() shouldContain "Expected exception from fetchCredentials"

                // Should retry, and the second fetchCredentials call will work
                turbine.waitFor { it.connected }

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }
}

class LegacySyncIntegrationTest : BaseSyncIntegrationTest(false)

class NewSyncIntegrationTest : BaseSyncIntegrationTest(true) {
    // The legacy sync implementation doesn't prefetch credentials and doesn't support raw tables.

    @OptIn(LegacySyncImplementation::class)
    @Test
    fun testTokenPrefetch() =
        databaseTest {
            val prefetchCalled = CompletableDeferred<Unit>()
            val completePrefetch = CompletableDeferred<Unit>()
            var fetchCredentialsCount = 0

            val connector =
                object : PowerSyncBackendConnector() {
                    override suspend fun fetchCredentials(): PowerSyncCredentials? {
                        fetchCredentialsCount++
                        if (fetchCredentialsCount == 2) {
                            prefetchCalled.complete(Unit)
                            completePrefetch.await()
                        }

                        return TestConnector.testCredentials
                    }

                    override suspend fun uploadData(database: PowerSyncDatabase) {}
                }

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connect(connector, 1000L, retryDelayMs = 5000, options = getOptions())
                turbine.waitFor { it.connecting }

                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 4000))
                turbine.waitFor { it.connected }
                fetchCredentialsCount shouldBe 1

                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 10))
                prefetchCalled.await()
                // Should still be connected before prefetch completes
                database.currentStatus.connected shouldBe true

                // After the prefetch completes, we should reconnect
                completePrefetch.complete(Unit)
                turbine.waitFor { !it.connected }

                turbine.waitFor { it.connected }
                fetchCredentialsCount shouldBe 2
                turbine.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @OptIn(ExperimentalPowerSyncAPI::class, LegacySyncImplementation::class)
    fun rawTables() =
        databaseTest(createInitialDatabase = false) {
            val db =
                openDatabase(
                    Schema(
                        listOf(
                            RawTable(
                                name = "lists",
                                put =
                                    PendingStatement(
                                        "INSERT OR REPLACE INTO lists (id, name) VALUES (?, ?)",
                                        listOf(
                                            PendingStatementParameter.Id,
                                            PendingStatementParameter.Column("name"),
                                        ),
                                    ),
                                delete =
                                    PendingStatement(
                                        "DELETE FROM lists WHERE id = ?",
                                        listOf(PendingStatementParameter.Id),
                                    ),
                            ),
                        ),
                    ),
                )

            db.execute("CREATE TABLE lists (id TEXT NOT NULL PRIMARY KEY, name TEXT)")
            turbineScope(timeout = 10.0.seconds) {
                val query =
                    db
                        .watch("SELECT * FROM lists", throttleMs = 0L) {
                            it.getString(0) to it.getString(1)
                        }.testIn(this)
                query.awaitItem() shouldBe emptyList()

                db.connect(connector, options = getOptions())
                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "1",
                            checksums = listOf(BucketChecksum("a", checksum = 0)),
                        ),
                    ),
                )
                syncLines.send(
                    SyncLine.SyncDataBucket(
                        bucket = "a",
                        data =
                            listOf(
                                OplogEntry(
                                    checksum = 0L,
                                    data =
                                        JsonUtil.json.encodeToString(
                                            mapOf(
                                                "name" to "custom list",
                                            ),
                                        ),
                                    op = OpType.PUT,
                                    opId = "1",
                                    rowId = "my_list",
                                    rowType = "lists",
                                ),
                            ),
                        after = null,
                        nextAfter = null,
                    ),
                )
                syncLines.send(SyncLine.CheckpointComplete("1"))

                query.awaitItem() shouldBe listOf("my_list" to "custom list")

                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "2",
                            checksums = listOf(BucketChecksum("a", checksum = 0)),
                        ),
                    ),
                )
                syncLines.send(
                    SyncLine.SyncDataBucket(
                        bucket = "a",
                        data =
                            listOf(
                                OplogEntry(
                                    checksum = 0L,
                                    data = null,
                                    op = OpType.REMOVE,
                                    opId = "2",
                                    rowId = "my_list",
                                    rowType = "lists",
                                ),
                            ),
                        after = null,
                        nextAfter = null,
                    ),
                )
                syncLines.send(SyncLine.CheckpointComplete("1"))

                query.awaitItem() shouldBe emptyList()
                query.cancelAndIgnoreRemainingEvents()
            }
        }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun bson() =
        databaseTest {
            // There's no up-to-date bson library for Kotlin multiplatform, so this test verifies BSON support with byte
            // strings created with package:bson in Dart.
            syncLinesContentType = ContentType("application", "vnd.powersync.bson-stream")

            turbineScope(timeout = 10.0.seconds) {
                val query =
                    database
                        .watch("SELECT name FROM users", throttleMs = 0L) {
                            it.getString(0)!!
                        }.testIn(this)
                query.awaitItem() shouldBe emptyList()

                database.connect(connector, options = getOptions())

                // {checkpoint: {last_op_id: 1, write_checkpoint: null, buckets: [{bucket: a, checksum: 0, priority: 3, count: null}]}}
                syncLines.send(
                    "8100000003636865636b706f696e740070000000026c6173745f6f705f6964000200000031000a77726974655f636865636b706f696e7400046275636b657473003e00000003300036000000026275636b65740002000000610010636865636b73756d0000000000107072696f7269747900030000000a636f756e740000000000"
                        .hexToByteArray(),
                )

                // {data: {bucket: a, data: [{checksum: 0, data: {"name":"username"}, op: PUT, op_id: 1, object_id: u, object_type: users}]}}
                syncLines.send(
                    "9e00000003646174610093000000026275636b6574000200000061000464617461007a0000000330007200000010636865636b73756d0000000000026461746100140000007b226e616d65223a22757365726e616d65227d00026f70000400000050555400026f705f696400020000003100026f626a6563745f696400020000007500026f626a6563745f74797065000600000075736572730000000000"
                        .hexToByteArray(),
                )

                // {checkpoint_complete: {last_op_id: 1}}
                syncLines.send(
                    "3100000003636865636b706f696e745f636f6d706c6574650017000000026c6173745f6f705f6964000200000031000000".hexToByteArray(),
                )

                query.awaitItem() shouldBe listOf("username")
                query.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ends iteration on http close`() =
        databaseTest {
            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                database.connect(TestConnector(), options = getOptions())
                turbine.waitFor { it.connected }

                syncLines.close()
                turbine.waitFor { !it.connected }

                turbine.cancelAndIgnoreRemainingEvents()
            }
        }
}
