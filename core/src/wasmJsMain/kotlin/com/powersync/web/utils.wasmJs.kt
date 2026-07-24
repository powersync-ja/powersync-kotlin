@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

import kotlin.js.length

internal actual fun Long.toBigInt(): JsAny {
    return toJsBigInt()
}

internal actual fun JsAny.bigIntToLong(): Long {
    return unsafeCast<JsBigInt>().toLong()
}

internal actual fun JsAny.asByteArray(): ByteArray {
    val sourceArray = unsafeCast<JsArray<JsNumber>>()
    val array = ByteArray(sourceArray.length)
    for (i in 0..<sourceArray.length) {
        array[i] = sourceArray[i]!!.toInt().toByte()
    }

    return array
}
