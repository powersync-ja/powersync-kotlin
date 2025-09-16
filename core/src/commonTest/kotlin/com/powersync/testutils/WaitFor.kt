package com.powersync.testutils

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal suspend inline fun waitFor(
    timeout: Duration = 500.milliseconds,
    interval: Duration = 100.milliseconds,
    test: () -> Unit,
) {
    val begin = TimeSource.Monotonic.markNow()
    do {
        try {
            test()
            return
        } catch (e: Error) {
            // Treat exceptions as failed
            println("waitFor: failed with $e")
        }
        delay(interval)
    } while (begin.elapsedNow() < timeout)

    throw Exception("waitFor() Timeout reached")
}
