package com.powersync

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class DatabaseDriverFactory(
    private val context: Context,
) {
    internal actual fun addPowerSyncExtension(driver: BundledSQLiteDriver) {
        driver.addExtension("libpowersync.so", "sqlite3_powersync_init")
    }

    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String {
        return context.getDatabasePath(dbFilename).path
    }
}
