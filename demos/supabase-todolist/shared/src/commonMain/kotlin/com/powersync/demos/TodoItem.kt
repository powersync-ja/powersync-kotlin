package com.powersync.demos

internal data class TodoItem(
    val id: Long = 0L,
    val text: String = "",
    val isDone: Boolean = false
)
