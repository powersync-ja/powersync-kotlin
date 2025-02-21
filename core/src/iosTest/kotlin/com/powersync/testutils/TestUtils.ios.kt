package com.powersync.testutils

import com.powersync.DatabaseDriverFactory
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

actual val factory: DatabaseDriverFactory
    get() = DatabaseDriverFactory()

actual fun cleanup(path: String) {
    SystemFileSystem.delete(Path(path))
}
