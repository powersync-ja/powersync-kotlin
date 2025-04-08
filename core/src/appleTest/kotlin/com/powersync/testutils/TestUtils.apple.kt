package com.powersync.testutils

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

/**
 * We could use SystemTemporaryDirectory here in future, but we return null here
 * to skip tests which rely on a temporary directory for iOS.
 * The reason for skipping these tests is that the SQLiteR library does not currently
 * support opening DB paths for custom directories.
 */
actual fun getTempDir(): String? = null
