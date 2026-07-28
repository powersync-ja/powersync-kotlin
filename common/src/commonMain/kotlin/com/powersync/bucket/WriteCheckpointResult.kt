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
