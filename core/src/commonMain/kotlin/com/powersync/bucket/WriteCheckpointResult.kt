package com.powersync.bucket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class WriteCheckpointResponse(
    val data: WriteCheckpointData,
)

@Serializable
internal data class WriteCheckpointData(
    @SerialName("write_checkpoint") val writeCheckpoint: String,
)
