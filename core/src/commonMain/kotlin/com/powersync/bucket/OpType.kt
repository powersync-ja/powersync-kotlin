package com.powersync.bucket

import kotlinx.serialization.Serializable

@Serializable
internal enum class OpType(
    private val value: Int,
) {
    CLEAR(1),
    MOVE(2),
    PUT(3),
    REMOVE(4),
    ;

    override fun toString(): String = value.toString()
}
