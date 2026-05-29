package com.powersync.internal

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal actual fun platformAllowsWebSockets(): Boolean {
    // Of course the web support web sockets, but we don't want to use RSocket here since
    // all browsers properly support backpressure through fetch()
    return false
}

internal actual val ioCoroutineContext: CoroutineContext
    get() = EmptyCoroutineContext
