@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

internal actual fun Long.toSuitableJavaScriptRepresentation(): JsAny {
    val asInt = toInt()
    if (this == asInt.toLong()) {
        return asInt.toJsNumber()
    }

    throw UnsupportedOperationException("Binding long values larger than 32 bits is not supported on Kotlin/JS.")
}

internal actual fun JsAny.interpretAsLong(): Long {
    if (this is JsNumber) {
        return this.toLong()
    }

    // It's a big int, which we can't represent in Kotlin directly. Extract high and low i32 to
    // compose long.
    val high = bigIntLowBits(bigIntShiftRight(this, i32BigInt)).toLong()
    val low = bigIntLowBits(this).toLong()
    return (high shr 32) or low
}

private val i32BigInt = bigInt(32.toJsNumber())
private fun bigIntShiftRight(a: JsAny, b: JsAny) = js("a >> b")

private fun bigIntLowBits(bigInt: JsAny): JsNumber = js("Number(bigInt) | 0")
