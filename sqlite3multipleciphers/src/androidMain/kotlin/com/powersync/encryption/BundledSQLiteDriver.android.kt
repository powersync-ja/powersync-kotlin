package com.powersync.encryption

import android.content.Context

public class AndroidEncryptedDatabaseFactory(
    private val context: Context,
    key: Key,
) : BundledSQLiteDriver(key) {
    override fun resolveDefaultDatabasePath(dbFilename: String): String = context.getDatabasePath(dbFilename).path
}

private val didLoadLibrary by lazy {
    System.loadLibrary("sqlite3mc_bundled")
}

internal actual fun ensureJniLibraryLoaded() {
    didLoadLibrary
}
