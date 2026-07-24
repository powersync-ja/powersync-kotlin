@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

import com.powersync.internal.InternalPowerSyncAPI

internal actual fun Long.toBigInt(): JsAny =
    throw UnsupportedOperationException("Binding long values larger than 32 bits is not supported on Kotlin/JS.")

internal actual fun JsAny.bigIntToLong(): Long {
    // We can't represent big integers in Kotlin directly, extract high and low i32 values to
    // compose the long value.
    val high = bigIntLowBits(bigIntShiftRight(this, i32BigInt)).toLong()
    val low = bigIntLowBits(this).toLong()
    return (high shl 32) or (low and 0xFFFFFFFFL)
}

private val i32BigInt = bigInt(32.toJsNumber())

private fun bigIntShiftRight(
    a: JsAny,
    b: JsAny,
) = js("a >> b")

private fun bigIntLowBits(bigInt: JsAny): JsNumber = js("Number(bigInt) | 0")

@InternalPowerSyncAPI
internal actual fun Uint8Array.asByteArray(): ByteArray {
    // Kotlin uses Int8Array as a byte array representation, so we just need to convert.
    val array = this
    return js("new Int8Array(array.buffer, array.byteOffset, array.byteLength)")
}

@InternalPowerSyncAPI
internal actual fun ByteArray.copyAsArrayBuffer(length: Int): ArrayBuffer {
    val source = this
    return js("source.slice(0, length).buffer")
}
