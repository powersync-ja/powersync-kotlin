package com.powersync.sync

import android.os.Build

internal actual fun userAgent(): String = "PowerSync Kotlin SDK (Android ${Build.VERSION.SDK_INT})"
