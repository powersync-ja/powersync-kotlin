package com.powersync.testutils

import com.powersync.DatabaseDriverFactory
import java.io.File

actual typealias IgnoreOnAndroid = org.junit.Ignore

actual val factory: DatabaseDriverFactory
    get() = error("Unsupported")

actual fun cleanup(path: String) {
    File(path).delete()
}
