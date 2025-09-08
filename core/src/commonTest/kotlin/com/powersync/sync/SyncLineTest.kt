package com.powersync.sync

import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.StreamPriority
import com.powersync.utils.JsonUtil
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(LegacySyncImplementation::class)
class SyncLineTest {
    private fun checkDeserializing(
        expected: SyncLine,
        json: String,
    ) {
        assertEquals(expected, JsonUtil.json.decodeFromString<SyncLine>(json))
    }

    @Test
    fun testDeserializeCheckpoint() {
        checkDeserializing(
            SyncLine.FullCheckpoint(
                Checkpoint(
                    lastOpId = "10",
                    checksums = listOf(),
                ),
            ),
            """{"checkpoint": {"last_op_id": "10", "buckets": []}}""",
        )
    }

    @Test
    fun testDeserializeCheckpointNoPriority() {
        checkDeserializing(
            SyncLine.FullCheckpoint(
                Checkpoint(
                    lastOpId = "10",
                    checksums = listOf(BucketChecksum(bucket = "a", priority = StreamPriority(3), checksum = 10)),
                ),
            ),
            """{"checkpoint": {"last_op_id": "10", "buckets": [{"bucket": "a", "checksum": 10}]}}""",
        )
    }

    @Test
    fun testDeserializeCheckpointWithPriority() {
        checkDeserializing(
            SyncLine.FullCheckpoint(
                Checkpoint(
                    lastOpId = "10",
                    checksums = listOf(BucketChecksum(bucket = "a", priority = StreamPriority(1), checksum = 10)),
                ),
            ),
            """{"checkpoint": {"last_op_id": "10", "buckets": [{"bucket": "a", "priority": 1, "checksum": 10}]}}""",
        )
    }

    @Test
    fun testDeserializeCheckpointDiff() {
        checkDeserializing(
            SyncLine.CheckpointDiff(
                lastOpId = "10",
                updatedBuckets = listOf(),
                removedBuckets = listOf(),
            ),
            """{"checkpoint_diff": {"last_op_id": "10", "buckets": [], "updated_buckets": [], "removed_buckets": []}}""",
        )
    }

    @Test
    fun testDeserializeCheckpointComplete() {
        checkDeserializing(SyncLine.CheckpointComplete(lastOpId = "10"), """{"checkpoint_complete": {"last_op_id": "10"}}""")
    }

    @Test
    fun testDeserializePartialCheckpointComplete() {
        checkDeserializing(
            SyncLine.CheckpointPartiallyComplete(
                lastOpId = "10",
                priority = StreamPriority(1),
            ),
            """{"partial_checkpoint_complete": {"last_op_id": "10", "priority": 1}}""",
        )
    }

    @Test
    fun testDeserializeData() {
        checkDeserializing(
            SyncLine.SyncDataBucket(
                bucket = "bkt",
                data = emptyList(),
                after = null,
                nextAfter = null,
            ),
            """{"data": {"bucket": "bkt", "data": [], "after": null, "next_after": null}}""",
        )
    }

    @Test
    fun testKeepAlive() {
        checkDeserializing(SyncLine.KeepAlive(100), """{"token_expires_in": 100}""")
    }

    @Test
    fun testDeserializeUnknown() {
        checkDeserializing(SyncLine.UnknownSyncLine, """{"unknown_key": true}""")
    }
}
