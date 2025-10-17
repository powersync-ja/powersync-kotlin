package com.powersync

import androidx.sqlite.SQLiteConnection
import com.powersync.sqlite.Database

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
public actual class DatabaseDriverFactory: PersistentDriverFactory {
    actual override val platform: PowerSyncPlatform
        get() = BuiltinPlatform

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
}

internal actual fun openInMemoryConnection(): SQLiteConnection = DatabaseDriverFactory().openConnection(":memory:", 0x02)
