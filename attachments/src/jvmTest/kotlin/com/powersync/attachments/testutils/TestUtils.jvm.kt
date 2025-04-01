package com.powersync.attachments.testutils

import com.powersync.DatabaseDriverFactory
import java.io.File

actual val factory: DatabaseDriverFactory
    get() = DatabaseDriverFactory()

actual fun cleanup(path: String) {
    File(path).delete()
}
