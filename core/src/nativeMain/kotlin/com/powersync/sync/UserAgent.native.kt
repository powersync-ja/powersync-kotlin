package com.powersync.sync

import com.powersync.build.LIBRARY_VERSION
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun userAgent(): String = "PowerSync Kotlin SDK v$LIBRARY_VERSION (running on ${Platform.cpuArchitecture.name} ${Platform.osFamily.name})"
