package com.powersync.test

import com.powersync.PersistentConnectionFactory
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

expect val factory: PersistentConnectionFactory

fun cleanup(path: String) {
    val resolved = Path(path)
    if (SystemFileSystem.exists(resolved)) {
        SystemFileSystem.delete(resolved)
    }
}

fun getTempDir(): String {
    return SystemTemporaryDirectory.toString()
}
