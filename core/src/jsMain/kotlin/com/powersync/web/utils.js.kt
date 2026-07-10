@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

internal actual fun Long.toSuitableJavaScriptRepresentation(): JsAny {
    val asInt = toInt()
    if (this == asInt.toLong()) {
        return asInt.toJsNumber()
    }

    throw UnsupportedOperationException("Binding long values larger than 32 bits is not supported on Kotlin/JS.")
}
