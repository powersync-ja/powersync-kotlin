package com.powersync

import androidx.sqlite.SQLiteConnection
import com.powersync.sqlite.Database
import com.powersync.sqlite.SqliteException

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
public actual class DatabaseDriverFactory {
    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String = appleDefaultDatabasePath(dbFilename)

    internal actual fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection {
        val db = Database.open(path, openFlags)
        try {
            db.loadExtension(powerSyncExtensionPath, "sqlite3_powersync_init")
        } catch (e: SqliteException) {
            db.close()
            throw e
        }
        return db
    }
}
