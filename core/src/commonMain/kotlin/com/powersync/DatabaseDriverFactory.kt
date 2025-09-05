package com.powersync

import androidx.sqlite.SQLiteConnection

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class DatabaseDriverFactory {
    internal fun resolveDefaultDatabasePath(dbFilename: String): String

    /**
     * Opens a SQLite connection on [path] with [openFlags].
     *
     * The connection should have the PowerSync core extension loaded.
     */
    internal fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection
}

@OptIn(ExperimentalPowerSyncAPI::class)
internal fun openDatabase(
    factory: DatabaseDriverFactory,
    dbFilename: String,
    dbDirectory: String?,
    readOnly: Boolean = false,
): SQLiteConnection {
    val dbPath =
        if (dbDirectory != null) {
            "$dbDirectory/$dbFilename"
        } else {
            factory.resolveDefaultDatabasePath(dbFilename)
        }

    return factory.openConnection(
        dbPath,
        if (readOnly) {
            SQLITE_OPEN_READONLY
        } else {
            SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE
        },
    )
}

private const val SQLITE_OPEN_READONLY = 0x01
private const val SQLITE_OPEN_READWRITE = 0x02
private const val SQLITE_OPEN_CREATE = 0x04
