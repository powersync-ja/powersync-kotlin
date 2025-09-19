package com.powersync.utils

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Throttles an upstream flow.
 *
 * When a new event is emitted on this (upstream) flow, it is passed on downstream. For each value
 * passed downstream, the resulting flow will pause for at least [window] (or longer if emitting
 * the value downstream takes longer).
 *
 * While this flow is paused, no further events are passed downstream. The latest upstream event
 * emitted during the pause state is buffered and handled once the pause is over.
 *
 * In other words, this flow will _drop events_, so it should only be used when the upstream flow
 * serves as a notification marker (meaning that something downstream needs to run in response to
 * events, but the actual event does not matter).
 */
internal fun <T> Flow<T>.throttle(window: Duration): Flow<T> =
    flow {
        // Use a buffer before throttle (ensure only the latest event is kept)
        val bufferedFlow = this@throttle.buffer(Channel.CONFLATED)

        bufferedFlow.collect { value ->
            // Pause for the downstream emit or the delay window, whatever is longer
            val pauseUntil = TimeSource.Monotonic.markNow() + window
            emit(value)

            // Negating the duration because we want to pause until pauseUntil has passed.
            delay(-pauseUntil.elapsedNow())

            // The next incoming event will be provided from the buffer.
            // The next collect will emit the trailing edge
        }
    }
