package com.powersync.testutils

import com.powersync.DatabaseDriverFactory
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSTemporaryDirectory

actual val factory: DatabaseDriverFactory
    get() = DatabaseDriverFactory()

actual fun cleanup(path: String) {
    val resolved = Path(path)
    if (SystemFileSystem.exists(resolved)) {
        SystemFileSystem.delete(resolved)
    }
}

actual fun getTempDir(): String = NSTemporaryDirectory()

actual fun isIOS(): Boolean = true
