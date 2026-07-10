@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

internal actual fun Long.toSuitableJavaScriptRepresentation(): JsAny {
    return toJsBigInt()
}

internal actual fun JsAny.interpretAsLong(): Long {
    return unsafeCast<JsBigInt>().toLong()
}
