package com.powersync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class DatabaseDriverFactory {
    internal fun resolveDefaultDatabasePath(dbFilename: String): String
}

/**
 * Registers the PowerSync core extension on connections opened by this [BundledSQLiteDriver].
 *
 * This method will be invoked by the PowerSync SDK when creating new databases. When using
 * [PowerSyncDatabase.opened] with an existing connection pool, you should configure the driver
 * backing that pool to load the extension.
 */
@ExperimentalPowerSyncAPI()
public expect fun BundledSQLiteDriver.addPowerSyncExtension()

@OptIn(ExperimentalPowerSyncAPI::class)
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

    driver.addPowerSyncExtension()
    return driver.open(
        dbPath,
        if (readOnly) {
            SQLITE_OPEN_READONLY
        } else {
            SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE
        },
    )
}
