package com.powersync.sync

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun userAgent(): String = "PowerSync Kotlin SDK (running on ${Platform.cpuArchitecture.name} ${Platform.osFamily.name})"
