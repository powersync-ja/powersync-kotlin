@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.Promise
import kotlin.js.js
import kotlin.js.length
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class PowerSyncMutexTest {
    @Test
    fun `can acquire mutex`() =
        runTest {
            val mutex = maybeSharedMutex("can-acquire")
            mutex.acquire().use { }
        }

    @Test
    fun `can abort`() =
        runTest {
            val mutex = maybeSharedMutex("can-abort")
            val held = mutex.acquire()

            withContext(Dispatchers.Default) {
                shouldThrow<CancellationException> {
                    withTimeout(100.milliseconds) {
                        mutex.acquire()
                    }
                }
            }

            // Should not have a pending lock request after aborting.
            val pending = pendingRequests().await()
            pending.length shouldBe 0

            held.close()
        }

    @Test
    fun tryAcquire() =
        runTest {
            val mutex = maybeSharedMutex("try-acquire")
            val held = mutex.tryAcquire()!!

            mutex.tryAcquire() shouldBe null
            held.close()
        }
}

private fun pendingRequests(): Promise<JsArray<JsAny>> = js("navigator.locks.query().then((e) => e.pending)")
