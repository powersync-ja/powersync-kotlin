package com.powersync.db

import com.powersync.PowerSyncException

public fun <R> runWrapped(block: () -> R): R = try {
    block()
} catch (t: Throwable) {
    if (t is PowerSyncException) {
        throw t
    } else {
        throw PowerSyncException(t.message ?: "Unknown internal exception", t)
    }
}

public suspend fun <R> runWrappedSuspending(block: suspend () -> R): R = try {
    block()
} catch (t: Throwable) {
    if (t is PowerSyncException) {
        throw t
    } else {
        throw PowerSyncException(t.message ?: "Unknown internal exception", t)
    }
}