package com.powersync.sync

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.turbineScope
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketPriority
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.OpType
import com.powersync.bucket.OplogEntry
import com.powersync.testutils.ActiveDatabaseTest
import com.powersync.testutils.databaseTest
import com.powersync.testutils.waitFor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncProgressTest {
    private var lastOpId = 0

    @BeforeTest
    fun resetOpId() {
        lastOpId = 0
    }

    private fun bucket(
        name: String,
        count: Int,
        priority: BucketPriority = BucketPriority(3),
    ): BucketChecksum =
        BucketChecksum(
            bucket = name,
            priority = priority,
            checksum = 0,
            count = count,
        )

    private suspend fun ActiveDatabaseTest.addDataLine(
        bucket: String,
        amount: Int,
    ) {
        syncLines.send(
            SyncLine.SyncDataBucket(
                bucket = bucket,
                data =
                    List(amount) {
                        OplogEntry(
                            checksum = 0,
                            opId = (++lastOpId).toString(),
                            op = OpType.PUT,
                            rowId = lastOpId.toString(),
                            rowType = bucket,
                            data = "{}",
                        )
                    },
                after = null,
                nextAfter = null,
            ),
        )
    }

    private suspend fun ActiveDatabaseTest.addCheckpointComplete(priority: BucketPriority? = null) {
        if (priority != null) {
            syncLines.send(
                SyncLine.CheckpointPartiallyComplete(
                    lastOpId = lastOpId.toString(),
                    priority = priority,
                ),
            )
        } else {
            syncLines.send(SyncLine.CheckpointComplete(lastOpId = lastOpId.toString()))
        }
    }

    private suspend fun ReceiveTurbine<SyncStatusData>.expectProgress(
        total: Pair<Int, Int>,
        priorities: Map<BucketPriority, Pair<Int, Int>> = emptyMap(),
    ) {
        val item = awaitItem()
        val progress = item.downloadProgress ?: error("Expected download progress on $item")

        assertTrue { item.downloading }
        assertEquals(total.first, progress.downloadedOperations)
        assertEquals(total.second, progress.totalOperations)

        priorities.forEach { (priority, expected) ->
            val (expectedDownloaded, expectedTotal) = expected
            val progress = progress.untilPriority(priority)
            assertEquals(expectedDownloaded, progress.downloadedOperations)
            assertEquals(expectedTotal, progress.totalOperations)
        }
    }

    private suspend fun ReceiveTurbine<SyncStatusData>.expectNotDownloading() {
        awaitItem().also {
            assertFalse { it.downloading }
            assertNull(it.downloadProgress)
        }
    }

    @Test
    fun withoutPriorities() =
        databaseTest {
            database.connect(connector)

            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                // Send checkpoint with 10 ops, progress should be 0/10
                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "10",
                            checksums = listOf(bucket("a", 10)),
                        ),
                    ),
                )
                turbine.expectProgress(0 to 10)

                addDataLine("a", 10)
                turbine.expectProgress(10 to 10)

                addCheckpointComplete()
                turbine.expectNotDownloading()

                // Emit new data, progress should be 0/2 instead of 10/12
                syncLines.send(
                    SyncLine.CheckpointDiff(
                        lastOpId = "12",
                        updatedBuckets = listOf(bucket("a", 12)),
                        removedBuckets = emptyList(),
                    ),
                )
                turbine.expectProgress(0 to 2)

                addDataLine("a", 2)
                turbine.expectProgress(2 to 2)

                addCheckpointComplete()
                turbine.expectNotDownloading()

                turbine.cancel()
            }

            database.close()
            syncLines.close()
        }

    @Test
    fun interruptedSync() =
        databaseTest {
            database.connect(connector)

            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                // Send checkpoint with 10 ops, progress should be 0/10
                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "10",
                            checksums = listOf(bucket("a", 10)),
                        ),
                    ),
                )
                turbine.expectProgress(0 to 10)

                addDataLine("a", 5)
                turbine.expectProgress(5 to 10)

                turbine.cancel()
            }

            // Emulate the app closing
            database.close()
            syncLines.close()

            // And reconnecting
            database = openDatabase()
            syncLines = Channel()
            database.connect(connector)

            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                // Send the same checkpoint as before
                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "10",
                            checksums = listOf(bucket("a", 10)),
                        ),
                    ),
                )

                // Progress should be restored: 5 / 10 instead of 0/5
                turbine.expectProgress(5 to 10)

                addDataLine("a", 5)
                turbine.expectProgress(10 to 10)
                addCheckpointComplete()
                turbine.expectNotDownloading()

                turbine.cancel()
            }

            database.close()
            syncLines.close()
        }

    @Test
    fun interruptedSyncWithNewCheckpoint() =
        databaseTest {
            database.connect(connector)

            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }
                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "10",
                            checksums = listOf(bucket("a", 10)),
                        ),
                    ),
                )
                turbine.expectProgress(0 to 10)

                addDataLine("a", 5)
                turbine.expectProgress(5 to 10)

                turbine.cancel()
            }

            // Close and re-connect
            database.close()
            syncLines.close()
            database = openDatabase()
            syncLines = Channel()
            database.connect(connector)

            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                // Send checkpoint with two more ops
                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "12",
                            checksums = listOf(bucket("a", 12)),
                        ),
                    ),
                )

                turbine.expectProgress(5 to 12)

                addDataLine("a", 7)
                turbine.expectProgress(12 to 12)
                addCheckpointComplete()
                turbine.expectNotDownloading()

                turbine.cancel()
            }

            database.close()
            syncLines.close()
        }

    @Test
    fun differentPriorities() =
        databaseTest {
            database.connect(connector)

            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                suspend fun expectProgress(
                    prio0: Pair<Int, Int>,
                    prio2: Pair<Int, Int>,
                ) {
                    turbine.expectProgress(prio2, mapOf(BucketPriority(0) to prio0, BucketPriority(2) to prio2))
                }

                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "10",
                            checksums =
                                listOf(
                                    bucket("a", 5, BucketPriority(0)),
                                    bucket("b", 5, BucketPriority(2)),
                                ),
                        ),
                    ),
                )
                expectProgress(0 to 5, 0 to 10)

                addDataLine("a", 5)
                expectProgress(5 to 5, 5 to 10)

                addCheckpointComplete(BucketPriority(0))
                expectProgress(5 to 5, 5 to 10)

                addDataLine("b", 2)
                expectProgress(5 to 5, 7 to 10)

                // Before syncing b fully, send a new checkpoint
                syncLines.send(
                    SyncLine.CheckpointDiff(
                        lastOpId = "14",
                        updatedBuckets =
                            listOf(
                                bucket("a", 8, BucketPriority(0)),
                                bucket("b", 6, BucketPriority(2)),
                            ),
                        removedBuckets = emptyList(),
                    ),
                )
                expectProgress(5 to 8, 7 to 14)

                addDataLine("a", 3)
                expectProgress(8 to 8, 10 to 14)
                addDataLine("b", 4)
                expectProgress(8 to 8, 14 to 14)

                addCheckpointComplete()
                turbine.expectNotDownloading()

                turbine.cancel()
            }

            database.close()
            syncLines.close()
        }
}
