@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.js

internal external interface LockManager : JsAny {
    fun request(
        name: String,
        options: JsAny,
        callback: (lock: Lock?) -> Promise<JsAny?>,
    ): Promise<JsAny>
}

internal external interface NavigatorLocksOwner : JsAny {
    val locks: LockManager
}

internal external interface Lock

internal fun navigator(): NavigatorLocksOwner = js("navigator")
