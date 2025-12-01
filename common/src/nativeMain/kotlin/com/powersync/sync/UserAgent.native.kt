package com.powersync.sync

import com.powersync.build.LIBRARY_VERSION
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun userAgent(): String =
    "PowerSync Kotlin SDK v$LIBRARY_VERSION (running on ${Platform.cpuArchitecture.name} ${Platform.osFamily.name})"

@OptIn(ExperimentalNativeApi::class)
internal actual fun defaultClientImplementationSupportsBackpressure(): Boolean {
    return when (Platform.osFamily) {
        // The NSURLSession API does not properly support backpressure for streaming HTTP responses. While it is
        // possible to call suspend() and resume() on NSURLSessionTask, those are ignored sometimes.
        OsFamily.IOS, OsFamily.WATCHOS, OsFamily.TVOS, OsFamily.MACOSX -> false
        else -> true
    }
}
