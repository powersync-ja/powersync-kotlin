package com.powersync.sync

import android.os.Build
import com.powersync.build.LIBRARY_VERSION

internal actual fun userAgent(): String = "PowerSync Kotlin SDK v$LIBRARY_VERSION (Android ${Build.VERSION.SDK_INT})"
