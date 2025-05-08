package com.powersync

import android.content.Context
import com.powersync.db.JdbcSqliteDriver
import com.powersync.db.buildDefaultWalProperties
import com.powersync.db.internal.InternalSchema
import com.powersync.db.migrateDriver
import kotlinx.coroutines.CoroutineScope
import org.sqlite.SQLiteCommitListener
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class DatabaseDriverFactory(
    private val context: Context,
) {
    internal actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
        dbDirectory: String?,
        readOnly: Boolean,
    ): PsSqlDriver {
        val schema = InternalSchema

        val dbPath =
            if (dbDirectory != null) {
                "$dbDirectory/$dbFilename"
            } else {
                context.getDatabasePath(dbFilename)
            }

        val properties = buildDefaultWalProperties(readOnly = readOnly)
        val isFirst = IS_FIRST_CONNECTION.getAndSet(false)
        if (isFirst) {
            // Make sure the temp_store_directory points towards a temporary directory we actually
            // have access to. Due to sandboxing, the default /tmp/ is inaccessible.
            // The temp_store_directory pragma is deprecated and not thread-safe, so we only set it
            // on the first connection (it sets a global field and will affect every connection
            // opened).
            val escapedPath = context.cacheDir.absolutePath.replace("\"", "\"\"")
            properties.setProperty("temp_store_directory", "\"$escapedPath\"")
        }

        val driver =
            JdbcSqliteDriver(
                url = "jdbc:sqlite:$dbPath",
                properties = properties,
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

    private companion object {
        val IS_FIRST_CONNECTION = AtomicBoolean(true)
    }
}
