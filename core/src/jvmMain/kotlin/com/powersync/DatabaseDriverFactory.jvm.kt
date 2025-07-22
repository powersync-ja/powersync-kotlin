package com.powersync

import androidx.sqlite.SQLiteConnection
import com.powersync.db.loadExtensions
import com.powersync.db.setSchemaVersion
import com.powersync.internal.driver.ConnectionListener
import com.powersync.internal.driver.JdbcConnection
import com.powersync.internal.driver.JdbcDriver

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "SqlNoDataSourceInspection")
public actual class DatabaseDriverFactory {
    internal actual fun openDatabase(
        dbFilename: String,
        dbDirectory: String?,
        readOnly: Boolean,
        listener: ConnectionListener?
    ): SQLiteConnection {
        val dbPath =
            if (dbDirectory != null) {
                "$dbDirectory/$dbFilename"
            } else {
                dbFilename
            }

        val driver = JdbcDriver()
        val connection = driver.openDatabase(dbPath, readOnly, listener) as JdbcConnection
        connection.setSchemaVersion()
        connection.loadExtensions(
            powersyncExtension to "sqlite3_powersync_init",
        )

        return connection
    }

    public companion object {
        private val powersyncExtension: String = extractLib("powersync")
    }
}
