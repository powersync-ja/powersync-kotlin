package com.powersync.internal

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun platformAllowsWebSockets(): Boolean = Platform.osFamily != OsFamily.WATCHOS
