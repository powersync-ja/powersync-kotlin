@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

/**
 * On Kotlin/JS, [Long.toJsBigInt] doesn't actually return a big int.
 *
 * This attempts to convert the long value into an exact JS representation, or fails if
 * that's impossible.
 */
internal expect fun Long.toSuitableJavaScriptRepresentation(): JsAny
