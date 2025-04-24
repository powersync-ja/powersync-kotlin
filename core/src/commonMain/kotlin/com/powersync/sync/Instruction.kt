package com.powersync.sync

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

@Serializable(with = Instruction.Serializer::class)
internal sealed interface Instruction {
    @Serializable
    data class LogLine(val severity: String, val line: String): Instruction

    @Serializable
    data class UpdateSyncStatus(val status: CoreSyncStatus): Instruction

    @Serializable
    data class EstablishSyncStream(val request: JsonObject): Instruction

    object FlushSileSystem: Instruction
    object CloseSyncStream: Instruction
    object UnknownInstruction: Instruction

    class Serializer : KSerializer<Instruction> {
        private val logLine = serializer<LogLine>()
        private val updateSyncStatus = serializer<UpdateSyncStatus>()
        private val establishSyncStream = serializer<EstablishSyncStream>()
        private val flushFileSystem = buildClassSerialDescriptor(FlushSileSystem::class.qualifiedName!!) {}
        private val closeSyncStream = buildClassSerialDescriptor(CloseSyncStream::class.qualifiedName!!) {}

        override val descriptor =
            buildClassSerialDescriptor(SyncLine::class.qualifiedName!!) {
                element("LogLine", logLine.descriptor, isOptional = true)
                element("UpdateSyncStatus", updateSyncStatus.descriptor, isOptional = true)
                element("EstablishSyncStream", establishSyncStream.descriptor, isOptional = true)
                element("FlushFileSystem", flushFileSystem, isOptional = true)
                element("CloseSyncStream", closeSyncStream, isOptional = true)
            }

        override fun deserialize(decoder: Decoder): Instruction =
            decoder.decodeStructure(descriptor) {
                val value =
                    when (val index = decodeElementIndex(descriptor)) {
                        0 -> decodeSerializableElement(descriptor, 0, logLine)
                        1 -> decodeSerializableElement(descriptor, 1, updateSyncStatus)
                        2 -> decodeSerializableElement(descriptor, 2, establishSyncStream)
                        3 -> FlushSileSystem
                        4 -> CloseSyncStream
                        CompositeDecoder.UNKNOWN_NAME, CompositeDecoder.DECODE_DONE -> UnknownInstruction
                        else -> error("Unexpected index: $index")
                    }

                if (decodeElementIndex(descriptor) != CompositeDecoder.DECODE_DONE) {
                    // Sync lines are single-key objects, make sure there isn't another one.
                    UnknownInstruction
                } else {
                    value
                }
            }

        override fun serialize(encoder: Encoder, value: Instruction) {
            // We don't need this functionality, so...
            throw UnsupportedOperationException("Serializing instructions")
        }
    }
}

@Serializable
internal class CoreSyncStatus {}
