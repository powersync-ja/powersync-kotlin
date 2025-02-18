package com.powersync.sync

import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketPriority
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.OplogEntry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import kotlinx.serialization.serializer

@Serializable(with = SyncLineSerializer::class)
internal sealed interface SyncLine {
    data class FullCheckpoint(val checkpoint: Checkpoint): SyncLine

    @Serializable
    data class CheckpointDiff(
        @SerialName("last_op_id") val lastOpId: String,
        @SerialName("updated_buckets") val updatedBuckets: List<BucketChecksum>,
        @SerialName("removed_buckets") val removedBuckets: List<String>,
        @SerialName("write_checkpoint") val writeCheckpoint: String? = null,
    ): SyncLine

    @Serializable
    data class CheckpointComplete(@SerialName("last_op_id") val lastOpId: String): SyncLine

    @Serializable
    data class CheckpointPartiallyComplete(
        @SerialName("last_op_id") val lastOpId: String,
        @SerialName("priority") val priority: BucketPriority
    ): SyncLine

    @Serializable
    data class SyncDataBucket(
        val bucket: String,
        val data: List<OplogEntry>,
        @SerialName("has_more") val hasMore: Boolean = false,
        val after: String?,
        @SerialName("next_after")val nextAfter: String?,
    ): SyncLine

    data class KeepAlive(
        val tokenExpiresIn: Int
    ): SyncLine

    data object UnknownSyncLine : SyncLine
}

private class SyncLineSerializer : KSerializer<SyncLine> {
    private val checkpoint = serializer<Checkpoint>();
    private val checkpointDiff = serializer<SyncLine.CheckpointDiff>()
    private val checkpointComplete = serializer<SyncLine.CheckpointComplete>()
    private val checkpointPartiallyComplete = serializer<SyncLine.CheckpointPartiallyComplete>()
    private val data = serializer<SyncLine.SyncDataBucket>()

    override val descriptor = buildClassSerialDescriptor(SyncLine::class.qualifiedName!!) {
        element("checkpoint", checkpoint.descriptor, isOptional = true)
        element("checkpoint_diff", checkpointDiff.descriptor, isOptional = true)
        element("checkpoint_complete", checkpointComplete.descriptor, isOptional = true)
        element("partial_checkpoint_complete", checkpointPartiallyComplete.descriptor, isOptional = true)
        element("data", data.descriptor, isOptional = true)
        element<Int>("token_expires_in", isOptional = true)
    }

    override fun deserialize(decoder: Decoder): SyncLine = decoder.decodeStructure(descriptor) {
        val value = when (val index = decodeElementIndex(descriptor)) {
            0 -> SyncLine.FullCheckpoint(decodeSerializableElement(descriptor, 0, checkpoint))
            1 -> decodeSerializableElement(descriptor, 1, checkpointDiff)
            2 -> decodeSerializableElement(descriptor, 2, checkpointComplete)
            3 -> decodeSerializableElement(descriptor, 3, checkpointPartiallyComplete)
            4 -> decodeSerializableElement(descriptor, 4, data)
            5 -> SyncLine.KeepAlive(decodeIntElement(descriptor, 5))
            else -> error("Unexpected index: $index")
        }

        if (decodeElementIndex(descriptor) != CompositeDecoder.DECODE_DONE) {
            // Sync lines are single-key objects, make sure there isn't another one.
            SyncLine.UnknownSyncLine
        } else {
            value
        }
    }

    override fun serialize(encoder: Encoder, value: SyncLine) {
        encoder.encodeStructure(descriptor) {
            when (value) {
                is SyncLine.FullCheckpoint -> encodeSerializableElement(descriptor, 0, checkpoint, value.checkpoint)
                is SyncLine.CheckpointDiff -> encodeSerializableElement(descriptor, 1, checkpointDiff, value)
                is SyncLine.CheckpointComplete -> encodeSerializableElement(descriptor, 2, checkpointComplete, value)
                is SyncLine.CheckpointPartiallyComplete -> encodeSerializableElement(descriptor, 3, checkpointPartiallyComplete, value)
                is SyncLine.SyncDataBucket -> encodeSerializableElement(descriptor, 4, data, value)
                is SyncLine.KeepAlive -> encodeIntElement(descriptor, 5, value.tokenExpiresIn)
                is SyncLine.UnknownSyncLine -> throw UnsupportedOperationException("Can't serialize unknown sync line")
            }
        }
    }

}
