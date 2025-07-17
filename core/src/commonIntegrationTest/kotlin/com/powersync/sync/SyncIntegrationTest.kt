package com.powersync.sync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.TestConnector
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
import com.powersync.db.schema.PendingStatement
import com.powersync.db.schema.PendingStatementParameter
import com.powersync.db.schema.RawTable
import com.powersync.db.schema.Schema
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
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
            database.connect(connector, options = options)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }
                turbine.cancel()
            }
        }

    @Test
    fun useParameters() =
        databaseTest {
            database.connect(connector, options = options, params = mapOf("foo" to JsonParam.String("bar")))
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
            database.connect(connector, options = options)

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
            database.connect(connector, options = options)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected }
                connector.cachedCredentials shouldNotBe null

                database.disconnect()
                turbine.waitFor { !it.connected }
                turbine.cancel()
            }

            // Disconnecting should have closed the channel
            waitFor { syncLines.isClosedForSend shouldBe true }

            // And called invalidateCredentials on the connector
            connector.cachedCredentials shouldBe null
        }

    @Test
    fun cannotUpdateSchemaWhileConnected() =
        databaseTest {
            database.connect(connector, options = options)

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
            database.connect(connector, options = options)

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
            database.connect(connector, options = options)

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
            database.connect(connector, options = options)

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

                database.connect(connector, options = options)
                turbine.waitFor { it.connecting }

                database.disconnect()

                turbine.waitFor { !it.connecting && !it.connected }
                turbine.cancel()
            }
        }

    @Test
    fun testMultipleSyncsDoNotCreateMultipleStatusEntries() =
        databaseTest {
            database.connect(connector, options = options)

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
                database.connect(connector, options = options)
                db2.connect(connector, options = options)

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
                database.connect(connector, options = options)

                turbine1.waitFor { it.connecting }
                db2.connect(connector, options = options)

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

                database.connect(connector, 1000L, options = options)
                turbine.waitFor { it.connecting }

                database.disconnect()
                turbine.waitFor { !it.connecting }

                database.connect(connector, 1000L, options = options)
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

                database.connect(connector, 1000L, retryDelayMs = 5000, options = options)
                turbine.waitFor { it.connecting }

                database.connect(connector, 1000L, retryDelayMs = 5000, options = options)
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
            database.connect(testConnector, options = options)

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
                turbine.waitFor { it.connected && !it.uploading }
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

            database.execute("INSERT INTO users (id, name) VALUES (uuid(), ?)", listOf("local write"))
            database.connect(connector, options = options)

            turbineScope(timeout = 10.0.seconds) {
                val turbine = database.currentStatus.asFlow().testIn(scope)
                turbine.waitFor { it.connected }

                val query = database.watch("SELECT name FROM users") { it.getString(0)!! }.testIn(scope)
                query.awaitItem() shouldBe listOf("local write")

                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 1234))
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

                database.connect(connector, 1000L, retryDelayMs = 5000, options = options)
                turbine.waitFor { it.connecting }

                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 4000))
                turbine.waitFor { it.connected }
                fetchCredentialsCalls shouldBe 1

                // Should invalidate credentials when token expires
                syncLines.send(SyncLine.KeepAlive(tokenExpiresIn = 0))
                turbine.waitFor { !it.connected }
                connector.cachedCredentials shouldBe null

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

                database.connect(connector, 1000L, retryDelayMs = 5000, options = options)
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

                database.connect(connector, 1000L, retryDelayMs = 5000, options = options)
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
                                        listOf(PendingStatementParameter.Id, PendingStatementParameter.Column("name")),
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

                db.connect(connector, options = options)
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
}
