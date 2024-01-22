package co.powersync.bucket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WriteCheckpointResponse(
    val data: WriteCheckpointData
)

@Serializable
data class WriteCheckpointData(
    @SerialName("write_checkpoint") val writeCheckpoint: String
)