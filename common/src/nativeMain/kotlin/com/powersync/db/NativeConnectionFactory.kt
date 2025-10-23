package com.powersync.db

import androidx.sqlite.SQLiteConnection
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PersistentConnectionFactory
import com.powersync.PowerSyncException
import com.powersync.resolvePowerSyncLoadableExtensionPath
import com.powersync.sqlite.Database

/**
 * A [PersistentConnectionFactory] implementation delegating to static `sqlite3_` invocations through cinterop.
 */
public abstract class NativeConnectionFactory : PersistentConnectionFactory {
    @OptIn(ExperimentalPowerSyncAPI::class)
    override fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection {
        // On some platforms, most notably watchOS, there's no dynamic extension loading and the core extension is
        // registered via sqlite3_auto_extension.
        val extensionPath = resolvePowerSyncLoadableExtensionPath()
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

    override fun openInMemoryConnection(): SQLiteConnection = openConnection(":memory:", 0x02)
}
