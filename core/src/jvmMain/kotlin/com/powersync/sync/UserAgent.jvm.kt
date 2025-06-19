package com.powersync.sync

import com.powersync.build.LIBRARY_VERSION

internal actual fun userAgent(): String {
    val os = System.getProperty("os.name") ?: "unknown"
    val osVersion = System.getProperty("os.version") ?: ""
    val java = System.getProperty("java.vendor.version") ?: System.getProperty("java.runtime.version") ?: "unknown"

    return "PowerSync Kotlin SDK v${LIBRARY_VERSION} (running Java $java on $os $osVersion)"
}
