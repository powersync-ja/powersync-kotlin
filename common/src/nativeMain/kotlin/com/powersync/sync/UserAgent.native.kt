package com.powersync.sync

import com.powersync.build.LIBRARY_VERSION
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun userAgent(): String =
    "powersync-kotlin/v$LIBRARY_VERSION ${Platform.osFamily.name}"
