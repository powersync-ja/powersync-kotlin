package com.powersync.sync

import com.powersync.bucket.BucketPriority
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * An instruction sent to this SDK by the core extension to implement sync behavior.
 */
@Serializable(with = Instruction.Serializer::class)
internal sealed interface Instruction {
    @Serializable
    data class LogLine(
        val severity: String,
        val line: String,
    ) : Instruction

    @Serializable
    data class UpdateSyncStatus(
        val status: CoreSyncStatus,
    ) : Instruction

    @Serializable
    data class EstablishSyncStream(
        val request: JsonObject,
    ) : Instruction

    @Serializable
    data class FetchCredentials(
        @SerialName("did_expire")
        val didExpire: Boolean,
    ) : Instruction

    data object FlushSileSystem : Instruction

    data object CloseSyncStream : Instruction

    data object DidCompleteSync : Instruction

    data class UnknownInstruction(
        val raw: JsonElement?,
    ) : Instruction

    class Serializer : KSerializer<Instruction> {
        private val logLine = serializer<LogLine>()
        private val updateSyncStatus = serializer<UpdateSyncStatus>()
        private val establishSyncStream = serializer<EstablishSyncStream>()
        private val fetchCredentials = serializer<FetchCredentials>()
        private val flushFileSystem = serializer<JsonObject>()
        private val closeSyncStream = serializer<JsonObject>()
        private val didCompleteSync = serializer<JsonObject>()

        override val descriptor =
            buildClassSerialDescriptor(Instruction::class.qualifiedName!!) {
                element("LogLine", logLine.descriptor, isOptional = true)
                element("UpdateSyncStatus", updateSyncStatus.descriptor, isOptional = true)
                element("EstablishSyncStream", establishSyncStream.descriptor, isOptional = true)
                element("FetchCredentials", fetchCredentials.descriptor, isOptional = true)
                element("FlushFileSystem", flushFileSystem.descriptor, isOptional = true)
                element("CloseSyncStream", closeSyncStream.descriptor, isOptional = true)
                element("DidCompleteSync", didCompleteSync.descriptor, isOptional = true)
            }

        override fun deserialize(decoder: Decoder): Instruction =
            decoder.decodeStructure(descriptor) {
                val value =
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> decodeSerializableElement(descriptor, 0, logLine)
                        1 -> decodeSerializableElement(descriptor, 1, updateSyncStatus)
                        2 -> decodeSerializableElement(descriptor, 2, establishSyncStream)
                        3 -> decodeSerializableElement(descriptor, 3, fetchCredentials)
                        4 -> {
                            decodeSerializableElement(descriptor, 4, flushFileSystem)
                            FlushSileSystem
                        }
                        5 -> {
                            decodeSerializableElement(descriptor, 5, closeSyncStream)
                            CloseSyncStream
                        }
                        6 -> {
                            decodeSerializableElement(descriptor, 6, didCompleteSync)
                            DidCompleteSync
                        }
                        CompositeDecoder.UNKNOWN_NAME ->
                            UnknownInstruction(
                                decodeSerializableElement(descriptor, index, serializer<JsonElement>()),
                            )
                        CompositeDecoder.DECODE_DONE -> UnknownInstruction(null)
                        else -> error("Unexpected index: $index")
                    }

                if (decodeElementIndex(descriptor) != CompositeDecoder.DECODE_DONE) {
                    // Sync lines are single-key objects, make sure there isn't another one.
                    UnknownInstruction(null)
                } else {
                    value
                }
            }

        override fun serialize(
            encoder: Encoder,
            value: Instruction,
        ) {
            // We don't need this functionality, so...
            throw UnsupportedOperationException("Serializing instructions")
        }
    }
}

@Serializable
internal data class CoreSyncStatus(
    val connected: Boolean,
    val connecting: Boolean,
    val downloading: CoreDownloadProgress?,
    @SerialName("priority_status")
    val priorityStatus: List<CorePriorityStatus>,
)

@Serializable
internal data class CoreDownloadProgress(
    val buckets: Map<String, CoreBucketProgress>,
)

@Serializable
internal data class CoreBucketProgress(
    val priority: BucketPriority,
    @SerialName("at_last")
    val atLast: Long,
    @SerialName("since_last")
    val sinceLast: Long,
    @SerialName("target_count")
    val targetCount: Long,
)

@Serializable
internal data class CorePriorityStatus(
    val priority: BucketPriority,
    @SerialName("last_synced_at")
    @Serializable(with = InstantTimestampSerializer::class)
    val lastSyncedAt: Instant?,
    @SerialName("has_synced")
    val hasSynced: Boolean?,
)

private object InstantTimestampSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("kotlinx.datetime.Instant", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant = Instant.fromEpochSeconds(decoder.decodeLong())

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) {
        encoder.encodeLong(value.epochSeconds)
    }
}
