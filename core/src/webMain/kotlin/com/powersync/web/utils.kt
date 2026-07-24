@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

import com.powersync.internal.InternalPowerSyncAPI
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsBigInt
import kotlin.js.JsNumber
import kotlin.js.get
import kotlin.js.js
import kotlin.js.length
import kotlin.js.toInt
import kotlin.js.unsafeCast

/**
 * On Kotlin/JS, [Long.toJsBigInt] doesn't actually return a big int.
 *
 * This attempts to convert the long value into an exact JS representation, or fails if
 * that's impossible.
 */
internal expect fun Long.toBigInt(): JsAny

internal expect fun JsAny.bigIntToLong(): Long

internal fun bigInt(content: JsAny?): JsBigInt = js("BigInt(content)")

@InternalPowerSyncAPI
public external class ArrayBuffer: JsAny {
    public constructor(length: Int)
}

@InternalPowerSyncAPI
public external class DataView: JsAny {
    public constructor(buffer: ArrayBuffer)

    public fun getInt8(index: Int): Int
    public fun setInt8(index: Int, value: Int): Int
}

@InternalPowerSyncAPI
public external class Uint8Array: JsAny {
    public constructor(buffer: ArrayBuffer)
}

internal expect fun JsAny.asByteArray(): ByteArray