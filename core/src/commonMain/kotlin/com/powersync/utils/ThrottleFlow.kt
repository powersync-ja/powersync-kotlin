package com.powersync.utils

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/**
 * Throttles a flow with emissions on the leading and trailing edge.
 * Events, from the incoming flow, during the throttle window are discarded.
 * Events are discarded by using a conflated buffer.
 * This throttle method acts as a slow consumer, but backpressure is not a concern
 * due to the conflated buffer dropping events during the throttle window.
 */
internal fun <T> Flow<T>.throttle(windowMs: Long): Flow<T> =
    flow {
        // Use a buffer before throttle (ensure only the latest event is kept)
        val bufferedFlow = this@throttle.buffer(Channel.CONFLATED)

        bufferedFlow.collect { value ->
            // Emit the event immediately (leading edge)
            emit(value)

            // Delay for the throttle window to avoid emitting too frequently
            delay(windowMs)

            // The next incoming event will be provided from the buffer.
            // The next collect will emit the trailing edge
        }
    }
