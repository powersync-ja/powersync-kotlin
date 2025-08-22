package com.powersync

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class DatabaseDriverFactory(
    private val context: Context,
) {
    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String {
        return context.getDatabasePath(dbFilename).path
    }
}

public actual fun BundledSQLiteDriver.addPowerSyncExtension() {
    addExtension("libpowersync.so", "sqlite3_powersync_init")
}
