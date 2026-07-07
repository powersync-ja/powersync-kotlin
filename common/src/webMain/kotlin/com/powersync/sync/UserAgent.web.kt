package com.powersync.sync

import com.powersync.build.LIBRARY_VERSION
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

internal actual fun userAgent(): String = "powersync-kotlin/${LIBRARY_VERSION} web/${browserUserAgent()}"

@OptIn(ExperimentalWasmJsInterop::class)
private fun browserUserAgent(): String = js("navigator.userAgent")
