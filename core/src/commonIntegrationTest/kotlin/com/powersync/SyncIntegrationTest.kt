package com.powersync

import app.cash.turbine.turbineScope
import co.touchlab.kermit.ExperimentalKermitApi
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketPriority
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.OpType
import com.powersync.bucket.OplogEntry
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Schema
import com.powersync.sync.SyncLine
import com.powersync.testutils.PowerSyncTestFixtures
import com.powersync.testutils.UserRow
import com.powersync.testutils.waitFor
import com.powersync.utils.JsonUtil
import dev.mokkery.verify
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class SyncIntegrationTest: PowerSyncTestFixtures() {
    private suspend fun PowerSyncDatabase.expectUserCount(amount: Int) {
        val users = getAll("SELECT * FROM users;") { UserRow.from(it) }
        users shouldHaveSize amount
    }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun closesResponseStreamOnDatabaseClose() =
        databaseTest {
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
            withClue("Should have closed sync stream") {
                syncLines.isClosedForSend shouldBe true
            }
        }

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun cleansResourcesOnDisconnect() =
        databaseTest {
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
            withClue("Should have closed sync stream") {
                syncLines.isClosedForSend shouldBe true
            }

            // And called invalidateCredentials on the connector
            verify { connector.invalidateCredentials() }
        }

    @Test
    fun cannotUpdateSchemaWhileConnected() =
        databaseTest {
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
        databaseTest {
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
            database = openDatabaseAndInitialize()
            database.currentStatus.hasSynced shouldBe false
            database.currentStatus.statusForPriority(BucketPriority(1)).hasSynced shouldBe true
        }

    @Test
    fun setsDownloadingState() =
        databaseTest {
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
        }

    @Test
    fun setsConnectingState() =
        databaseTest {
            turbineScope(timeout = 10.0.seconds) {
                val syncStream = syncStream()
                val turbine = database.currentStatus.asFlow().testIn(this)

                database.connectInternal(syncStream, 1000L)
                turbine.waitFor { it.connecting }

                database.disconnect()

                turbine.waitFor { !it.connecting && !it.connected }
                turbine.cancel()
            }
        }

    @Test
    fun testMultipleSyncsDoNotCreateMultipleStatusEntries() =
        databaseTest {
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
        }

    @Test
    @OptIn(ExperimentalKermitApi::class)
    fun warnsMultipleConnectionAttempts() =
        databaseTest {
            val db2 = openDatabaseAndInitialize()

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
        }

    @Test
    fun queuesMultipleConnectionAttempts() =
        databaseTest {
            val db2 = openDatabaseAndInitialize()

            turbineScope(timeout = 10.0.seconds) {
                val turbine1 = database.currentStatus.asFlow().testIn(this)
                val turbine2 = db2.currentStatus.asFlow().testIn(this)

                // Connect the first database
                database.connect(connector, 1000L)

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
}
