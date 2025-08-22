package com.powersync

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "SqlNoDataSourceInspection")
public actual class DatabaseDriverFactory {
    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String {
        return dbFilename
    }
}

public actual fun BundledSQLiteDriver.addPowerSyncExtension() {
    addExtension(powersyncExtension, "sqlite3_powersync_init")
}

private val powersyncExtension: String by lazy { extractLib("powersync") }
