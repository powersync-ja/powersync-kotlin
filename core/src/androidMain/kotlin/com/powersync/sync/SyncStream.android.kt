package com.powersync.sync

import android.os.Build

internal actual fun getOS(): String {
    val base = Build.VERSION.BASE_OS
    val version = Build.VERSION.SDK_INT
    return "android $base/$version"
}
