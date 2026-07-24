@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

import androidx.sqlite.SQLiteException
import com.powersync.internal.InternalPowerSyncAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsBigInt
import kotlin.js.Promise
import kotlin.js.asJsException
import kotlin.js.js
import kotlin.js.toInt
import kotlin.js.unsafeCast

/**
 * On Kotlin/JS, [Long.toJsBigInt] doesn't actually return a JavaScript big int.
 *
 * This attempts to convert the long value into an exact JS big int representation, or fails if
 * that's impossible.
 */
internal expect fun Long.toBigInt(): JsAny

internal expect fun JsAny.bigIntToLong(): Long

internal fun bigInt(content: JsAny?): JsBigInt = js("BigInt(content)")

@InternalPowerSyncAPI
public external class ArrayBuffer : JsAny {
    public constructor(length: Int)
}

@InternalPowerSyncAPI
public external class DataView : JsAny {
    public constructor(buffer: ArrayBuffer)

    public fun getInt8(index: Int): Int

    public fun setInt8(
        index: Int,
        value: Int,
    ): Int
}

@InternalPowerSyncAPI
public external class Uint8Array : JsAny {
    public constructor(buffer: ArrayBuffer)
}

/**
 * Converts a JavaScript Uint8Array to a Kotlin byte array.
 *
 * This is a cheap cast on Kotlin/JS, on Kotlin/Wasm we have to copy.
 */
@InternalPowerSyncAPI
internal expect fun Uint8Array.asByteArray(): ByteArray

/**
 * Converts a Kotlin byte array to a JavaScript array buffer.
 *
 * This always copies, but this is still much cheaper on Kotlin/JS because we can use
 * JavaScript methods that boil down to a `memcpy`. For Kotlin/Wasm, this needs to copy
 * byte-by-byte.
 */
@InternalPowerSyncAPI
internal expect fun ByteArray.copyAsArrayBuffer(length: Int = size): ArrayBuffer

/**
 * Awaits on a promise created by the `sqlite3_web` package.
 *
 * Instead of cancelling early, this only supports cancellations when the promise completes with an
 * abort error.
 * Additionally, it maps common errors from the workers into appropriate Kotlin exceptions.
 */
@OptIn(InternalPowerSyncAPI::class)
@Suppress("SuspendCoroutineLacksCancellationGuarantees")
internal suspend fun <T : JsAny?> Promise<T>.awaitSafe(): T =
    suspendCoroutine { cont ->
        this@awaitSafe.then(
            onFulfilled = {
                cont.resume(it)
                null
            },
            onRejected = { rejection ->
                val asAny = rejection as JsAny
                var customException: Throwable? = null

                if (asAny is RemoteError) {
                    asAny.cause?.let { cause ->
                        if (isSqliteException(cause)) {
                            customException = cause.unsafeCast<SqliteException>().toAndroidxSqliteException()
                        } else {
                            val name = domErrorName(cause)
                            if (name == "AbortError") {
                                customException = CancellationException("Aborted in web worker")
                            }
                        }
                    }
                }

                cont.resumeWithException(customException ?: rejection.asJsException())
                null
            },
        )
    }

/**
 * Runs a suspending function with a JavaScript abort signal hooked to its job.
 */
@OptIn(InternalPowerSyncAPI::class)
internal suspend fun <T> withAbortSignal(block: suspend CoroutineScope.(JsAny) -> T): T {
    val controller = AbortController()
    var isCompleted = false

    return coroutineScope {
        val abortOnCancellation =
            launch {
                try {
                    awaitCancellation()
                } finally {
                    if (!isCompleted) {
                        controller.abort()
                    }
                }
            }

        try {
            block(controller.signal)
        } finally {
            isCompleted = true
            abortOnCancellation.cancel()
        }
    }
}

@InternalPowerSyncAPI
public external class AbortController : JsAny {
    public val signal: JsAny

    public fun abort()
}

private fun isSqliteException(exception: JsAny): Boolean = js("'extendedResultCode' in exception")

private fun domErrorName(domError: JsAny): String = js("domError.name")

@InternalPowerSyncAPI
private fun SqliteException.toAndroidxSqliteException(): SQLiteException {
    // toString() implementation copied from https://github.com/simolus3/sqlite3.dart/blob/main/sqlite3/lib/src/exception.dart#L56
    return SQLiteException(
        buildString {
            append("SqliteException(")
            append(extendedResultCode.toInt())
            append("): ")
            operation?.let { append("while $it, ") }
            append(message.toString())

            explanation?.let {
                append(", ")
                append(it)
            }

            causingStatement?.let {
                appendLine()
                append("  Causing statement")
                offset?.let { append(" (at position ${it.toInt()})") }
                append(": ")
                append(causingStatement)
            }
        },
    )
}
