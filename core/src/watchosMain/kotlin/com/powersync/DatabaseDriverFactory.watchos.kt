package com.powersync

import androidx.sqlite.SQLiteConnection
import com.powersync.sqlite.Database
import com.powersync.static.powersync_init_static

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
        resolvePowerSyncLoadableExtensionPath()

        return Database.open(path, openFlags)
    }
}

internal actual fun openInMemoryConnection(): SQLiteConnection = DatabaseDriverFactory().openConnection(":memory:", 0x02)
