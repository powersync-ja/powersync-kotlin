package com.powersync.bucket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.LongAsStringSerializer

@Serializable
internal data class WriteCheckpointResponse(
    val data: WriteCheckpointData,
)

@Serializable
internal data class WriteCheckpointData(
    @SerialName("write_checkpoint")
    @Serializable(with = LongAsStringSerializer::class)
    val writeCheckpoint: Long,
)

@Serializable
internal class CheckpointRequestPayload(
    @SerialName("client_id")
    val clientId: String,
    @SerialName("checkpoint_request_id")
    @Serializable(with = LongAsStringSerializer::class)
    val checkpointRequestId: Long,
)

@Serializable
internal class CheckpointRequestResponse(
    val data: CheckpointRequestResponseData,
)

@Serializable
internal class CheckpointRequestResponseData(
    @SerialName("checkpoint_request_id")
    @Serializable(with = LongAsStringSerializer::class)
    val id: Long,
)
