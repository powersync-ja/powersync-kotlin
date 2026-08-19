package com.powersync.sync

import com.powersync.bucket.StreamPriority
import com.powersync.db.crud.TypedRow
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
import kotlin.time.Instant

/**
 * An instruction sent to this SDK by the core extension to implement sync behavior.
 */
@Serializable(with = Instruction.Serializer::class)
internal sealed interface Instruction {
    /**
     * An [Instruction] that doesn't start or stop a sync iteration.
     */
    sealed interface NonInterruptingInstruction : Instruction

    @Serializable
    data class LogLine(
        val severity: String,
        val line: String,
    ) : NonInterruptingInstruction

    @Serializable
    data class UpdateSyncStatus(
        val status: CoreSyncStatus,
    ) : NonInterruptingInstruction

    @Serializable
    data class EstablishSyncStream(
        val request: JsonObject,
    ) : Instruction

    @Serializable
    data class FetchCredentials(
        @SerialName("did_expire")
        val didExpire: Boolean,
    ) : NonInterruptingInstruction

    @Serializable
    data class CloseSyncStream(
        @SerialName("hide_disconnect")
        val hideDisconnect: Boolean,
    ) : Instruction

    data object DidCompleteSync : NonInterruptingInstruction

    data class UnknownInstruction(
        val raw: JsonElement?,
    ) : NonInterruptingInstruction

    class Serializer : KSerializer<Instruction> {
        private val logLine = serializer<LogLine>()
        private val updateSyncStatus = serializer<UpdateSyncStatus>()
        private val establishSyncStream = serializer<EstablishSyncStream>()
        private val fetchCredentials = serializer<FetchCredentials>()
        private val closeSyncStream = serializer<CloseSyncStream>()
        private val didCompleteSync = serializer<JsonObject>()

        override val descriptor =
            buildClassSerialDescriptor("com.powersync.sync.Instruction") {
                element("LogLine", logLine.descriptor, isOptional = true)
                element("UpdateSyncStatus", updateSyncStatus.descriptor, isOptional = true)
                element("EstablishSyncStream", establishSyncStream.descriptor, isOptional = true)
                element("FetchCredentials", fetchCredentials.descriptor, isOptional = true)
                element("CloseSyncStream", closeSyncStream.descriptor, isOptional = true)
                element("DidCompleteSync", didCompleteSync.descriptor, isOptional = true)
            }

        override fun deserialize(decoder: Decoder): Instruction =
            decoder.decodeStructure(descriptor) {
                val value =
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> {
                            decodeSerializableElement(descriptor, 0, logLine)
                        }

                        1 -> {
                            decodeSerializableElement(descriptor, 1, updateSyncStatus)
                        }

                        2 -> {
                            decodeSerializableElement(descriptor, 2, establishSyncStream)
                        }

                        3 -> {
                            decodeSerializableElement(descriptor, 3, fetchCredentials)
                        }

                        4 -> {
                            decodeSerializableElement(descriptor, 5, closeSyncStream)
                        }

                        5 -> {
                            decodeSerializableElement(descriptor, 6, didCompleteSync)
                            DidCompleteSync
                        }

                        CompositeDecoder.UNKNOWN_NAME -> {
                            UnknownInstruction(
                                decodeSerializableElement(descriptor, index, serializer<JsonElement>()),
                            )
                        }

                        CompositeDecoder.DECODE_DONE -> {
                            UnknownInstruction(null)
                        }

                        else -> {
                            error("Unexpected index: $index")
                        }
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
    val streams: List<CoreActiveStreamSubscription>,
)

@Serializable
internal data class CoreActiveStreamSubscription(
    override val name: String,
    override val parameters: TypedRow?,
    val priority: StreamPriority?,
    val progress: ProgressInfo,
    override val active: Boolean,
    @SerialName("is_default")
    override val isDefault: Boolean,
    @SerialName("has_explicit_subscription")
    override val hasExplicitSubscription: Boolean,
    @SerialName("expires_at")
    @Serializable(with = InstantTimestampSerializer::class)
    override val expiresAt: Instant?,
    @SerialName("last_synced_at")
    @Serializable(with = InstantTimestampSerializer::class)
    override val lastSyncedAt: Instant?,
) : SyncSubscriptionDescription {
    override val hasSynced: Boolean
        get() = lastSyncedAt != null
}

@Serializable
internal data class CoreDownloadProgress(
    val buckets: Map<String, CoreBucketProgress>,
)

@Serializable
internal data class CoreBucketProgress(
    val priority: StreamPriority,
    @SerialName("at_last")
    val atLast: Long,
    @SerialName("since_last")
    val sinceLast: Long,
    @SerialName("target_count")
    val targetCount: Long,
)

@Serializable
internal data class CorePriorityStatus(
    val priority: StreamPriority,
    @SerialName("last_synced_at")
    @Serializable(with = InstantTimestampSerializer::class)
    val lastSyncedAt: Instant?,
    @SerialName("has_synced")
    val hasSynced: Boolean?,
)

private object InstantTimestampSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("kotlinx.datetime.Instant", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant = Instant.fromEpochMilliseconds(decoder.decodeLong() / 1000)

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) {
        encoder.encodeLong(value.toEpochMilliseconds() * 1000)
    }
}
