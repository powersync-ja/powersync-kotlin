package co.powersync.kotlin.bucket

import kotlinx.serialization.Serializable

@Serializable
enum class OpTypeEnum {
    CLEAR,
    MOVE,
    PUT,
    REMOVE
}
