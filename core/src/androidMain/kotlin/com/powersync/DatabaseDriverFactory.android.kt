package com.powersync

import android.content.Context
import androidx.sqlite.SQLiteConnection
import com.powersync.db.loadExtensions
import com.powersync.internal.driver.AndroidDriver
import com.powersync.internal.driver.ConnectionListener
import com.powersync.internal.driver.JdbcConnection

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class DatabaseDriverFactory(
    private val context: Context,
) {
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
                "${context.getDatabasePath(dbFilename)}"
            }

        val driver = AndroidDriver(context)
        val connection = driver.openDatabase(dbPath, readOnly, listener) as JdbcConnection
        connection.loadExtensions(
            "libpowersync.so" to "sqlite3_powersync_init",
        )

        return connection
    }
}
