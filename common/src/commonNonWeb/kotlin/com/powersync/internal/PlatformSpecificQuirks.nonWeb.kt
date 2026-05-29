package com.powersync.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext

internal actual val ioCoroutineContext: CoroutineContext
    get() = Dispatchers.IO
