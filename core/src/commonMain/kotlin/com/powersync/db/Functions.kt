package com.powersync.db

import com.powersync.PowerSyncException

internal fun <R> runWrapped(block: () -> R): R =
    runCatching(block).let { result ->
        if (result.isSuccess) {
            result.getOrThrow()
        } else {
            val thrownException = result.exceptionOrNull()
                ?: IllegalStateException() // This shouldn't be possible, but we want to make sure our thrown exceptions are all wrapped
            throw PowerSyncException(message = thrownException.message ?: "Unknown internal exception", thrownException)
        }
    }