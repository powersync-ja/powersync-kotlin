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
        } catch (_: Error) {
            // Treat exceptions as failed
        }
        delay(interval)
    } while (begin.elapsedNow() < timeout)

    throw Exception("Timeout reached")
}
