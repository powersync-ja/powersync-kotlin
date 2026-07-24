@file:OptIn(ExperimentalWasmJsInterop::class, InternalPowerSyncAPI::class)

package com.powersync.web

import com.powersync.internal.InternalPowerSyncAPI
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.toDouble
import kotlin.js.toInt
import kotlin.js.toJsArray
import kotlin.js.toJsNumber
import kotlin.js.toJsString
import kotlin.js.unsafeCast

/**
 * A typed buffer of SQL statement parameters.
 *
 * Statement parameters are encoded as JS values to send them to the Dart worker. Additionally, we
 * encode the type of parameters to allow workers to well integers encoded as doubles and actual
 * doubles apart.
 */
internal class TypedParameters {
    private val parameters = mutableListOf<JsAny?>()
    private var types = ByteArray(32)

    fun takeParameters(): JsArray<JsAny?> = parameters.toJsArray()

    /**
     * An array buffer encoding parameter types.
     */
    fun takeTypes(): ArrayBuffer = types.copyAsArrayBuffer(parameters.size)

    fun clear() {
        parameters.clear()
    }

    private fun ensureParameterCapacity(index: Int) {
        check(index > 0) // 1-based index

        if (parameters.size < index) {
            repeat(index - parameters.size) {
                parameters.add(null)
            }
        }

        if (types.size < index) {
            types = types.copyOf(maxOf(index, types.size * 2))
        }
    }

    fun bindBlob(
        index: Int,
        value: ByteArray,
    ) {
        ensureParameterCapacity(index)
        parameters[index - 1] = Uint8Array(value.copyAsArrayBuffer())
        types[index - 1] = TypeCodes.BLOB
    }

    fun bindDouble(
        index: Int,
        value: Double,
    ) {
        ensureParameterCapacity(index)
        parameters[index - 1] = value.toJsNumber()
        types[index - 1] = TypeCodes.FLOAT
    }

    fun bindInt(
        index: Int,
        value: Int,
    ) {
        ensureParameterCapacity(index)
        parameters[index - 1] = value.toJsNumber()
        types[index - 1] = TypeCodes.INTEGER
    }

    fun bindLong(
        index: Int,
        value: Long,
    ) {
        ensureParameterCapacity(index)
        val isInt = value.toInt().toLong() == value
        if (isInt) {
            parameters[index - 1] = value.toInt().toJsNumber()
            types[index - 1] = TypeCodes.INTEGER
        } else {
            parameters[index - 1] = value.toBigInt()
            types[index - 1] = TypeCodes.BIG_INTEGER
        }
    }

    fun bindText(
        index: Int,
        value: String,
    ) {
        ensureParameterCapacity(index)
        parameters[index - 1] = value.toJsString()
        types[index - 1] = TypeCodes.TEXT
    }

    fun bindNull(index: Int) {
        ensureParameterCapacity(index)
        parameters[index - 1] = null
        types[index - 1] = TypeCodes.NULL
    }
}

/**
 * Decodes a JavaScript value representing a SQLite result to Kotlin.
 */
internal fun decodeTyped(
    source: JsAny?,
    typeCode: Byte,
): Any? =
    when (typeCode) {
        TypeCodes.INTEGER -> source!!.unsafeCast<JsNumber>().toInt().toLong()
        TypeCodes.BIG_INTEGER -> source!!.bigIntToLong()
        TypeCodes.FLOAT -> source!!.unsafeCast<JsNumber>().toDouble()
        TypeCodes.TEXT -> source!!.unsafeCast<JsString>().toString()
        TypeCodes.BLOB -> source!!.unsafeCast<Uint8Array>().asByteArray()
        else -> null
    }

/**
 * A code describing the type and encoding of a SQLite value.
 *
 * For more information, see https://github.com/simolus3/sqlite3.dart/blob/268e9b585d9e7b337d205e0b6d342f92a8e00a79/sqlite3_web/lib/src/protocol/messages.dart#L195
 */
internal object TypeCodes {
    /**
     * An integer value encoded as JavaScript number.
     */
    const val INTEGER: Byte = 1

    /**
     * An integer value encoded as a JavaScript big integer.
     */
    const val BIG_INTEGER: Byte = 2

    /**
     * A double, encoded as a JavaScript number
     */
    const val FLOAT: Byte = 3

    /**
     * A string
     */
    const val TEXT: Byte = 4

    /**
     * A blob, encoded as a `Uint8Array` in JavaScript.
     */
    const val BLOB: Byte = 5

    /**
     * The null value.
     */
    const val NULL: Byte = 6
}
