package com.powersync.db

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncException
import kotlinx.coroutines.CancellationException

public fun <R> runWrapped(block: () -> R): R =
    try {
        block()
    } catch (t: Throwable) {
        if (t is PowerSyncException) {
            Logger.e("PowerSyncException: ${t.message}")
            throw t
        } else {
            Logger.e("PowerSyncException: ${t.message}")
            throw PowerSyncException(t.message ?: "Unknown internal exception", t)
        }
    }

public suspend fun <R> runWrappedSuspending(block: suspend () -> R): R =
    try {
        block()
    } catch (t: Throwable) {
        if (t is CancellationException) {
            throw t
        }

        if (t is PowerSyncException) {
            Logger.e("PowerSyncException: ${t.message}")
            throw t
        } else {
            Logger.e("PowerSyncException: ${t.message}")
            throw PowerSyncException(t.message ?: "Unknown internal exception", t)
        }
    }
