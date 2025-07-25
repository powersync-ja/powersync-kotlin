package com.powersync.internal.driver

import androidx.sqlite.SQLiteConnection

/**
 * An internal interface to open a SQLite connection that has the PowerSync core extension loaded.
 */
public interface PowerSyncDriver {
    /**
     * Opens a database at [path], without initializing the PowerSync core extension or running any
     * pragma statements that require the database to be accessible.
     */
    public fun openDatabase(
        path: String,
        readOnly: Boolean = false,
        listener: ConnectionListener? = null,
    ): SQLiteConnection
}

public interface ConnectionListener {
    public fun onCommit()

    public fun onRollback()

    public fun onUpdate(
        kind: Int,
        database: String,
        table: String,
        rowid: Long,
    )
}
