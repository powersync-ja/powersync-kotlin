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
        val extensionPath = resolvePowerSyncLoadableExtensionPath()
        // On native platforms, the path should be null since we link the extension statically.
        // Still, we need to call that method to register the extension to SQLite.
        check(extensionPath == null) {
            "Expected PowerSync core extension path to be null on native platforms."
        }

        return Database.open(path, openFlags)
    }

    override fun openInMemoryConnection(): SQLiteConnection = openConnection(":memory:", 0x02)
}
