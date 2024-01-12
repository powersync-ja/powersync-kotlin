package co.powersync.bucket

import kotlinx.serialization.Serializable

@Serializable
enum class OpType {
    CLEAR,
    MOVE,
    PUT,
    REMOVE;
}