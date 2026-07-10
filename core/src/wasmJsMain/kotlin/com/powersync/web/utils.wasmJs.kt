@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

internal actual fun Long.toSuitableJavaScriptRepresentation(): JsAny {
    return toJsBigInt()
}
