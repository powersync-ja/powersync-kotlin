package com.powersync

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class DatabaseDriverFactory(
    private val context: Context,
) {
    private val driver = BundledSQLiteDriver().also { it.addPowerSyncExtension() }

    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String = context.getDatabasePath(dbFilename).path

    internal actual fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection = driver.open(path, openFlags)
}

public fun BundledSQLiteDriver.addPowerSyncExtension() {
    addExtension("libpowersync.so", "sqlite3_powersync_init")
}
