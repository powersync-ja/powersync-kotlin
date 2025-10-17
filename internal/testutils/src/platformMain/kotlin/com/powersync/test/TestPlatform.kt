package com.powersync.test

import com.powersync.PersistentDriverFactory

class TestPlatform {
}

val factory: PersistentDriverFactory get() = TODO()

fun cleanup(path: String) {}

fun getTempDir(): String {
    TODO()
}

fun isIOS(): Boolean {
    TODO()
}
