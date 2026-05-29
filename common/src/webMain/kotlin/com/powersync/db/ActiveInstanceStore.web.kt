@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.db

import kotlin.js.ExperimentalWasmJsInterop

internal actual fun disposeWhenDeallocated(resource: ActiveDatabaseResource): Any {
    // TODO: Use FinalizationRegistry?
    return "unsupported"
}
