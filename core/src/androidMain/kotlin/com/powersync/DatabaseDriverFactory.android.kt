package com.powersync

import android.content.Context
import com.powersync.db.JdbcSqliteDriver
import com.powersync.db.buildDefaultWalProperties
import com.powersync.db.internal.InternalSchema
import com.powersync.db.migrateDriver
import kotlinx.coroutines.CoroutineScope
import org.sqlite.SQLiteCommitListener

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class DatabaseDriverFactory(
    private val context: Context,
) {
    internal actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
        dbDirectory: String?,
    ): PsSqlDriver {
        val schema = InternalSchema

        val dbPath =
            if (dbDirectory != null) {
                "$dbDirectory/$dbFilename"
            } else {
                context.getDatabasePath(dbFilename)
            }

        val driver =
            JdbcSqliteDriver(
                url = "jdbc:sqlite:$dbPath",
                properties = buildDefaultWalProperties(),
            )

        migrateDriver(driver, schema)

        driver.loadExtensions(
            "libpowersync.so" to "sqlite3_powersync_init",
        )

        val mappedDriver = PsSqlDriver(driver = driver)

        driver.connection.database.addUpdateListener { _, _, table, _ ->
            mappedDriver.updateTable(table)
        }

        driver.connection.database.addCommitListener(
            object : SQLiteCommitListener {
                override fun onCommit() {
                    // We track transactions manually
                }

                override fun onRollback() {
                    mappedDriver.clearTableUpdates()
                }
            },
        )

        return mappedDriver
    }
}
