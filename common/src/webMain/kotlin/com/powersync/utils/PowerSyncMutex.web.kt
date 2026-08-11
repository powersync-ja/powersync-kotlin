@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.utils

import com.powersync.internal.InternalPowerSyncAPI
import com.powersync.web.LockManager
import com.powersync.web.navigator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsBoolean
import kotlin.js.Promise
import kotlin.js.asJsException
import kotlin.js.js
import kotlin.js.toBoolean

internal actual fun maybeSharedMutex(name: String): PowerSyncMutex = NavigatorLocksMutex(name)

@OptIn(InternalPowerSyncAPI::class)
private class NavigatorLocksMutex(
    private val name: String,
    private val locks: LockManager = navigator().locks,
) : PowerSyncMutex {
    override suspend fun acquire(owner: Any?): HeldMutex =
        withAbortSignal { signal ->
            // If we don't pass ifAvailable, this will never return null.
            acquireInternal(lockOptions(signal))!!
        }

    override suspend fun tryAcquire(owner: Any?): HeldMutex? = acquireInternal(ifAvailableLockOptions())

    private suspend fun acquireInternal(options: JsAny): HeldMutex? {
        val acquiredLock = CompletableDeferred<HeldMutex?>()

        locks
            .request(name, options) { lock ->
                Promise { resolve, _ ->
                    acquiredLock.complete(
                        if (lock == null) {
                            resolve(null)
                            null
                        } else {
                            PromiseBasedHeldMutex(resolve)
                        },
                    )
                }
            }.catch { rejection ->
                if (isAbortError(rejection as JsAny).toBoolean()) {
                    acquiredLock.completeExceptionally(CancellationException("Acquiring navigator lock $name cancelled"))
                } else {
                    acquiredLock.completeExceptionally(rejection.asJsException())
                }

                null
            }

        // Make this non-cancellable, we abort the lock request if needed.
        return withContext(NonCancellable) {
            acquiredLock.await()
        }
    }
}

private class PromiseBasedHeldMutex(
    private val resolve: (JsAny?) -> Unit,
) : HeldMutex {
    override fun close() {
        resolve(null)
    }
}

private fun lockOptions(abortSignal: JsAny): JsAny = js("({ signal: abortSignal })")

private fun ifAvailableLockOptions(): JsAny = js("({ ifAvailable: true })")

private fun isAbortError(e: JsAny): JsBoolean = js("e.name === 'AbortError'")
