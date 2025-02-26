package com.powersync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketPriority
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.OpType
import com.powersync.bucket.OplogEntry
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Schema
import com.powersync.sync.SyncLine
import com.powersync.sync.SyncStream
import com.powersync.testutils.MockSyncService
import com.powersync.testutils.UserRow
import com.powersync.testutils.cleanup
import com.powersync.testutils.waitFor
import com.powersync.utils.JsonUtil
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(co.touchlab.kermit.ExperimentalKermitApi::class)
class SyncIntegrationTest {
    private val logger =
        Logger(
            TestConfig(
                minSeverity = Severity.Debug,
                logWriterList = listOf(),
            ),
        )
    private lateinit var database: PowerSyncDatabaseImpl
    private lateinit var connector: PowerSyncBackendConnector
    private lateinit var syncLines: Channel<SyncLine>

    @BeforeTest
    fun setup() {
        cleanup("testdb")
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
        cleanup("testdb")
    }

    private fun openDb() =
        PowerSyncDatabase(
            factory = com.powersync.testutils.factory,
            schema = Schema(UserRow.table),
            dbFilename = "testdb",
        ) as PowerSyncDatabaseImpl

    private fun CoroutineScope.syncStream(): SyncStream {
        val client = MockSyncService.client(this, syncLines.receiveAsFlow())
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

    private suspend fun expectUserCount(amount: Int) {
        val users = database.getAll("SELECT * FROM users;") { UserRow.from(it) }
        assertEquals(amount, users.size, "Expected $amount users, got $users")
    }

    @Test
    fun testPartialSync() =
        runTest {
            val syncStream = syncStream()
            database.connect(syncStream, 1000L)

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
            database.connect(syncStream, 1000L)

            syncLines.send(
                SyncLine.FullCheckpoint(
                    Checkpoint(
                        lastOpId = "4",
                        checksums = listOf(BucketChecksum(bucket = "bkt", priority = BucketPriority(1), checksum = 0)),
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
}
