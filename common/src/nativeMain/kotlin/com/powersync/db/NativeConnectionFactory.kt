package com.powersync.db

import androidx.sqlite.SQLiteConnection
import com.powersync.PersistentConnectionFactory
import com.powersync.PowerSyncException
import com.powersync.sqlite.Database

/**
 * A [PersistentConnectionFactory] implementation delegating to static `sqlite3_` invocations through cinterop.
 */
public abstract class NativeConnectionFactory: PersistentConnectionFactory {
    override fun openConnection(path: String, openFlags: Int): SQLiteConnection {
        val extensionPath = powersyncLoadableExtensionPath()
        val db = Database.open(path, openFlags)

        if (extensionPath != null) {
            try {
                db.loadExtension(extensionPath, "sqlite3_powersync_init")
            } catch (e: PowerSyncException) {
                db.close()
                throw e
            }
        }

        return db
    }

    override fun openInMemoryConnection(): SQLiteConnection {
        return openConnection(":memory:", 0x02)
    }

    /**
     * If the core extension should be loaded as a dynamic library, returns its path.
     *
     * Otherwise, installs the core extension as a static extension and returns null.
     */
    protected abstract fun powersyncLoadableExtensionPath(): String?
}
