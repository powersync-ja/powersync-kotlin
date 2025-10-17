package com.powersync.db

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncException
import kotlinx.coroutines.CancellationException

/**
 * Runs the given [block], wrapping exceptions as [PowerSyncException]s.
 */
public inline fun <R> runWrapped(block: () -> R): R =
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

@Deprecated("Use runWrapped instead", replaceWith = ReplaceWith("runWrapped"))
public suspend fun <R> runWrappedSuspending(block: suspend () -> R): R =
    runWrapped {
        block()
    }
