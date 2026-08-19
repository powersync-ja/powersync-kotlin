@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.utils

import com.powersync.internal.InternalPowerSyncAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

/**
 * Runs a suspending function with a JavaScript abort signal hooked to its job.
 */
@InternalPowerSyncAPI
public suspend fun <T> withAbortSignal(block: suspend CoroutineScope.(JsAny) -> T): T {
    val controller = AbortController()
    var isCompleted = false

    return coroutineScope {
        val abortOnCancellation =
            launch {
                try {
                    awaitCancellation()
                } finally {
                    if (!isCompleted) {
                        controller.abort()
                    }
                }
            }

        try {
            block(controller.signal)
        } finally {
            isCompleted = true
            abortOnCancellation.cancel()
        }
    }
}

@InternalPowerSyncAPI
private external class AbortController : JsAny {
    val signal: JsAny

    fun abort()
}
