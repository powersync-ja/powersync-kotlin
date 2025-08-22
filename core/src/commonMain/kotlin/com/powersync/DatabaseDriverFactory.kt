package com.powersync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class DatabaseDriverFactory {
    internal fun addPowerSyncExtension(driver: BundledSQLiteDriver)

    internal fun resolveDefaultDatabasePath(dbFilename: String): String
}

internal fun openDatabase(
    factory: DatabaseDriverFactory,
    dbFilename: String,
    dbDirectory: String?,
    readOnly: Boolean = false,
): SQLiteConnection {
    val driver = BundledSQLiteDriver()
    val dbPath =
        if (dbDirectory != null) {
            "$dbDirectory/$dbFilename"
        } else {
            factory.resolveDefaultDatabasePath(dbFilename)
        }

    factory.addPowerSyncExtension(driver)

    return driver.open(dbPath, if (readOnly) {
        SQLITE_OPEN_READONLY
    } else {
        SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE
    })
}
