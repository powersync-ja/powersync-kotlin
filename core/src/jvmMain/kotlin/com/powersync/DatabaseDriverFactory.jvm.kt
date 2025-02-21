package com.powersync

import com.powersync.db.internal.InternalSchema
import kotlinx.coroutines.CoroutineScope
import org.sqlite.SQLiteCommitListener
import java.nio.file.Path

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "SqlNoDataSourceInspection")
public actual class DatabaseDriverFactory {
    internal actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
    ): PsSqlDriver {
        val schema = InternalSchema

        val driver =
            PSJdbcSqliteDriver(
                url = "jdbc:sqlite:$dbFilename",
                schema = schema,
            )
        // Generates SQLITE_BUSY errors
//        driver.enableWriteAheadLogging()
        driver.loadExtensions(
            powersyncExtension to "sqlite3_powersync_init",
        )

        val mappedDriver = PsSqlDriver(scope = scope, driver = driver)

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

    public companion object {
        private val powersyncExtension: Path = extractLib("powersync")
    }
}
