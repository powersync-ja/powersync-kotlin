@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

import com.powersync.internal.InternalPowerSyncAPI

internal actual fun Long.toBigInt(): JsAny = toJsBigInt()

internal actual fun JsAny.bigIntToLong(): Long = unsafeCast<JsBigInt>().toLong()

@InternalPowerSyncAPI
internal actual fun Uint8Array.asByteArray(): ByteArray {
    val sourceArray = unsafeCast<JsArray<JsNumber>>()
    val array = ByteArray(sourceArray.length)
    for (i in 0..<sourceArray.length) {
        array[i] = sourceArray[i]!!.toInt().toByte()
    }

    return array
}

@InternalPowerSyncAPI
internal actual fun ByteArray.copyAsArrayBuffer(length: Int): ArrayBuffer {
    val buffer = ArrayBuffer(length)
    val dataView = DataView(buffer)
    forEachIndexed { index, b ->
        if (index >= length) return@forEachIndexed
        dataView.setInt8(index, b.toInt())
    }
    return buffer
}
