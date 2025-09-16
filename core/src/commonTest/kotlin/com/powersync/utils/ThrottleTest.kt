package com.powersync.utils

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ThrottleTest {
    @Test
    fun testThrottle() {
        runTest {
            val t =
                flow {
                    emit(1)
                    delay(10)
                    emit(2)
                    delay(20)
                    emit(3)
                    delay(100)
                    emit(4)
                }.throttle(100.milliseconds)
                    .map {
                        // Adding a delay here to simulate a slow consumer
                        delay(1000)
                        it
                    }.toList()
            assertEquals(t, listOf(1, 4))
        }
    }

    @Test
    fun testWaitTimeIsNotAdditive() =
        runTest {
            val upstream =
                flow {
                    repeat(5) {
                        emit(it)
                        delay(10.seconds)
                    }
                }

            // If throttle were to start the delay after the downstream emit completed, it would 11
            // seconds, skipping events. That's not what we want though, the downstream delay is
            // long enough to not need additional throttling.
            val events =
                upstream
                    .throttle(5.seconds)
                    .map {
                        delay(6.seconds)
                        it
                    }.toList()

            events shouldBe listOf(0, 1, 2, 3, 4)
        }
}
