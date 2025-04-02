package com.powersync.sync

internal actual fun getOS(): String {
    val os = System.getProperty("os.name")
    val version = System.getProperty("os.version")
    return "jvm $os/$version"
}
