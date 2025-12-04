package com.powersync.encryption

import com.powersync.extractLib

public class JavaEncryptedDatabaseFactory(
    key: Key,
) : BundledSQLiteDriver(key) {
    override fun resolveDefaultDatabasePath(dbFilename: String): String = dbFilename
}

private val didLoadLibrary by lazy {
    val path = extractLib(JavaEncryptedDatabaseFactory::class, "sqlite3mc_jni")
    System.load(path)
}

internal actual fun ensureJniLibraryLoaded() {
    didLoadLibrary
}
