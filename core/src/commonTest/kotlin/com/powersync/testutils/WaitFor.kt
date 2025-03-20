package com.powersync.testutils

import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal suspend fun waitFor(
    timeout: Duration = 500.milliseconds,
    interval: Duration = 100.milliseconds,
    test: () -> Unit,
) {
    val begin = Clock.System.now().toEpochMilliseconds()
    do {
        try {
            test()
            return
        } catch (_: Error) {
            // Treat exceptions as failed
        }
        delay(interval.inWholeMilliseconds)
    } while ((Clock.System.now().toEpochMilliseconds() - begin) < timeout.inWholeMilliseconds)

    throw Exception("Timeout reached")
}
