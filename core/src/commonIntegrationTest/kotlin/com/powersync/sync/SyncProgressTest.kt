package com.powersync.sync

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.turbineScope
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketPriority
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.OpType
import com.powersync.bucket.OplogEntry
import com.powersync.bucket.StreamPriority
import com.powersync.testutils.ActiveDatabaseTest
import com.powersync.testutils.databaseTest
import com.powersync.testutils.waitFor
import io.kotest.assertions.withClue
import io.kotest.matchers.properties.shouldHaveValue
import kotlinx.coroutines.channels.Channel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(LegacySyncImplementation::class)
abstract class BaseSyncProgressTest(
    useNewSyncImplementation: Boolean,
) : AbstractSyncTest(
        useNewSyncImplementation,
    ) {
    private var lastOpId = 0

    @BeforeTest
    fun resetOpId() {
        lastOpId = 0
    }

    private fun bucket(
        name: String,
        count: Int,
        priority: StreamPriority = StreamPriority(3),
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

    private suspend fun ActiveDatabaseTest.addCheckpointComplete(priority: StreamPriority? = null) {
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

    private fun ProgressWithOperations.shouldBe(
        downloaded: Int,
        total: Int,
    ) {
        withClue("progress $downloadedOperations/$totalOperations should be $downloaded/$total") {
            this::downloadedOperations shouldHaveValue downloaded
            this::totalOperations shouldHaveValue total
        }
    }

    private suspend fun ReceiveTurbine<SyncStatusData>.expectProgress(
        total: Pair<Int, Int>,
        priorities: Map<StreamPriority, Pair<Int, Int>> = emptyMap(),
    ) {
        val item = awaitItem()
        val progress = item.downloadProgress ?: error("Expected download progress on $item")

        assertTrue { item.downloading }
        progress.shouldBe(total.first, total.second)

        priorities.forEach { (priority, expected) ->
            val (expectedDownloaded, expectedTotal) = expected
            val actualProgress = progress.untilPriority(priority)
            actualProgress.shouldBe(expectedDownloaded, expectedTotal)
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
            database.connect(connector, options = getOptions())

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
            database.connect(connector, options = getOptions())

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
            database.connect(connector, options = getOptions())

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
            database.connect(connector, options = getOptions())

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
            database.connect(connector, options = getOptions())

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
    fun interruptedWithDefrag() =
        databaseTest {
            database.connect(connector, options = getOptions())

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
            database.connect(connector, options = getOptions())

            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                // A sync rule deploy could reset buckets, making the new bucket smaller than the
                // existing one.
                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "14",
                            checksums = listOf(bucket("a", 4)),
                        ),
                    ),
                )

                // In this special case, don't report 5/4 as progress
                turbine.expectProgress(0 to 4)
                turbine.cancel()
            }

            database.close()
            syncLines.close()
        }

    @Test
    fun differentPriorities() =
        databaseTest {
            database.connect(connector, options = getOptions())

            turbineScope {
                val turbine = database.currentStatus.asFlow().testIn(this)
                turbine.waitFor { it.connected && !it.downloading }

                suspend fun expectProgress(
                    prio0: Pair<Int, Int>,
                    prio2: Pair<Int, Int>,
                ) {
                    turbine.expectProgress(
                        prio2,
                        mapOf(StreamPriority(0) to prio0, StreamPriority(2) to prio2),
                    )
                }

                syncLines.send(
                    SyncLine.FullCheckpoint(
                        Checkpoint(
                            lastOpId = "10",
                            checksums =
                                listOf(
                                    bucket("a", 5, StreamPriority(0)),
                                    bucket("b", 5, StreamPriority(2)),
                                ),
                        ),
                    ),
                )
                expectProgress(0 to 5, 0 to 10)

                addDataLine("a", 5)
                expectProgress(5 to 5, 5 to 10)

                addCheckpointComplete(StreamPriority(0))
                expectProgress(5 to 5, 5 to 10)

                addDataLine("b", 2)
                expectProgress(5 to 5, 7 to 10)

                // Before syncing b fully, send a new checkpoint
                syncLines.send(
                    SyncLine.CheckpointDiff(
                        lastOpId = "14",
                        updatedBuckets =
                            listOf(
                                bucket("a", 8, StreamPriority(0)),
                                bucket("b", 6, StreamPriority(2)),
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

class LegacySyncProgressTest : BaseSyncProgressTest(false)

class NewSyncProgressTest : BaseSyncProgressTest(true)
