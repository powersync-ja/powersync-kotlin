package com.powersync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "SqlNoDataSourceInspection")
public actual class DatabaseDriverFactory {
    private val driver = BundledSQLiteDriver().also { it.addPowerSyncExtension() }

    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String = dbFilename

    internal actual fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection = driver.open(path, openFlags)
}

public fun BundledSQLiteDriver.addPowerSyncExtension() {
    addExtension(powersyncExtension, "sqlite3_powersync_init")
}

private val powersyncExtension: String by lazy { extractLib("powersync") }
