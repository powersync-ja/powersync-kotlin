package com.powersync.db

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

@OptIn(ExperimentalNativeApi::class)
internal actual fun disposeWhenDeallocated(resource: ActiveDatabaseResource): Any {
    return createCleaner(resource) { it.dispose() }
}
