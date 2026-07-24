@file:OptIn(ExperimentalWasmJsInterop::class, InternalPowerSyncAPI::class)
package com.powersync.web

import com.powersync.internal.InternalPowerSyncAPI
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.get
import kotlin.js.length
import kotlin.js.toDouble
import kotlin.js.toInt
import kotlin.js.toJsArray
import kotlin.js.toJsNumber
import kotlin.js.toJsString
import kotlin.js.unsafeCast

internal class TypedParameters {
    private val parameters = mutableListOf<JsAny?>()
    private var types = ByteArray(32)

    fun takeParameters(): JsArray<JsAny?> {
        return parameters.toJsArray()
    }

    fun takeTypes(): ArrayBuffer {
        return types.toArrayBuffer(parameters.size)
    }

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

        if (types.size < index - 1) {
            types = types.copyOf(maxOf(index, types.size * 2))
        }
    }

    fun bindBlob(index: Int, value: ByteArray) {
        ensureParameterCapacity(index)
        parameters[index - 1] = Uint8Array(value.toArrayBuffer())
        types[index - 1] = TypeCodes.BLOB
    }

    fun bindDouble(index: Int, value: Double) {
        ensureParameterCapacity(index)
        parameters[index - 1] = value.toJsNumber()
        types[index - 1] = TypeCodes.FLOAT
    }

    fun bindInt(index: Int, value: Int) {
        ensureParameterCapacity(index)
        parameters[index - 1] = value.toJsNumber()
        types[index - 1] = TypeCodes.INTEGER
    }

    fun bindLong(index: Int, value: Long) {
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

    fun bindText(index: Int, value: String) {
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

internal fun decodeTyped(source: JsAny?, typeCode: Byte): Any? {
    return when(typeCode) {
        TypeCodes.INTEGER -> source!!.unsafeCast<JsNumber>().toInt().toLong()
        TypeCodes.BIG_INTEGER -> source!!.bigIntToLong()
        TypeCodes.FLOAT -> source!!.unsafeCast<JsNumber>().toDouble()
        TypeCodes.TEXT -> source!!.unsafeCast<JsString>().toString()
        TypeCodes.BLOB -> source!!.asByteArray()
        else -> null
    }
}

private fun ByteArray.toArrayBuffer(length: Int = size): ArrayBuffer {
    val buffer = ArrayBuffer(length)
    val dataView = DataView(buffer)
    forEachIndexed { index, b ->
        if (index >= length) return@forEachIndexed
        dataView.setInt8(index, b.toInt())
    }
    return buffer
}

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
    const val BOOLEAN: Byte = 7 // Unused, we encode booleans as integers.
}