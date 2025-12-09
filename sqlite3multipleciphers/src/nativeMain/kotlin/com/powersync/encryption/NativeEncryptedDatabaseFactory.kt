package com.powersync.encryption

import androidx.sqlite.SQLiteConnection
import com.powersync.appleDefaultDatabasePath
import com.powersync.db.NativeConnectionFactory

/**
 * A [NativeConnectionFactory] that links sqlite3multipleciphers and opens database with a [Key].
 */
public class NativeEncryptedDatabaseFactory(
    private val key: Key,
) : NativeConnectionFactory() {
    override fun resolveDefaultDatabasePath(dbFilename: String): String = appleDefaultDatabasePath(dbFilename)

    override fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection =
        super.openConnection(path, openFlags).apply {
            if (path != ":memory:") {
                // Settings keys for in-memory or temporary databases is not supported.
                encryptOrClose(key)
            }
        }
}
