package com.powersync

import com.powersync.db.internal.InternalSchema
import kotlinx.coroutines.CoroutineScope
import org.sqlite.SQLiteCommitListener
import java.nio.file.Path
import java.util.Properties

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "SqlNoDataSourceInspection")
public actual class DatabaseDriverFactory {
    internal actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
    ): PsSqlDriver {
        val schema = InternalSchema

        // WAL Mode properties
        val properties = Properties()
        properties.setProperty("journal_mode", "WAL")
        properties.setProperty("journal_size_limit", "${6 * 1024 * 1024}")
        properties.setProperty("busy_timeout", "30000")
        properties.setProperty("cache_size", "${50 * 1024}")

        val driver =
            PSJdbcSqliteDriver(
                url = "jdbc:sqlite:$dbFilename",
                schema = schema,
                properties = properties,
            )
        driver.loadExtensions(
            powersyncExtension to "sqlite3_powersync_init",
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

    public companion object {
        private val powersyncExtension: Path = extractLib("powersync")
    }
}
