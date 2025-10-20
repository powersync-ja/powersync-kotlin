package com.powersync

import androidx.sqlite.SQLiteConnection
import com.powersync.sqlite.Database

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
public actual class DatabaseDriverFactory : PersistentConnectionFactory {
    actual override fun resolveDefaultDatabasePath(dbFilename: String): String = appleDefaultDatabasePath(dbFilename)

    @OptIn(ExperimentalPowerSyncAPI::class)
    actual override fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection {
        // On some platforms, most notably watchOS, there's no dynamic extension loading and the core extension is
        // registered via sqlite3_auto_extension.
        val extensionPath = resolvePowerSyncLoadableExtensionPath()

        val db = Database.open(path, openFlags)
        extensionPath?.let { path ->
            try {
                db.loadExtension(path, "sqlite3_powersync_init")
            } catch (e: PowerSyncException) {
                db.close()
                throw e
            }
        }

        return db
    }

    actual override fun openInMemoryConnection(): SQLiteConnection = openConnection(":memory:", 0x02)
}

internal actual val inMemoryDriver: InMemoryConnectionFactory = DatabaseDriverFactory()
