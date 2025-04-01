package com.powersync.attachments.testutils

import com.powersync.DatabaseDriverFactory
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

actual val factory: DatabaseDriverFactory
    get() = DatabaseDriverFactory()

actual fun cleanup(path: String) {
    val resolved = Path(path)
    if (SystemFileSystem.exists(resolved)) {
        SystemFileSystem.delete(resolved)
    }
}
