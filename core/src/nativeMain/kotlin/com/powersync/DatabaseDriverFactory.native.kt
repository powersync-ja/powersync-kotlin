package com.powersync

import androidx.sqlite.SQLiteConnection
import com.powersync.sqlite.Database

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
public actual class DatabaseDriverFactory: PersistentConnectionFactory {
    actual override fun resolveDefaultDatabasePath(dbFilename: String): String = appleDefaultDatabasePath(dbFilename)

    @OptIn(ExperimentalPowerSyncAPI::class)
    actual override fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection {
        val db = Database.open(path, openFlags)
        try {
            db.loadExtension(resolvePowerSyncLoadableExtensionPath()!!, "sqlite3_powersync_init")
        } catch (e: PowerSyncException) {
            db.close()
            throw e
        }
        return db
    }

    actual override fun openInMemoryConnection(): SQLiteConnection {
        return openConnection(":memory:", 0x02)
    }
}

internal actual val inMemoryDriver: InMemoryConnectionFactory = DatabaseDriverFactory()
