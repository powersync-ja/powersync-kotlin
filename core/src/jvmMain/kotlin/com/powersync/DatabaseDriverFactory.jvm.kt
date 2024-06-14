package com.powersync

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.powersync.db.internal.InternalSchema
import java.util.Properties
import kotlinx.coroutines.CoroutineScope

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class DatabaseDriverFactory {
    private var driver: PsSqlDriver? = null
    private external fun setupSqliteBinding()

    @Suppress("unused")
    private fun onTableUpdate(tableName: String) {
        driver?.updateTable(tableName)
    }

    @Suppress("unused")
    private fun onTransactionCommit(success: Boolean) {
        driver?.also { driver ->
            if (success) {
                driver.fireTableUpdates()
            } else {
                driver.clearTableUpdates()
            }
        }
    }

    public actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String
    ): PsSqlDriver {
        val schema = InternalSchema.synchronous()
        this.driver = PsSqlDriver(
            scope = scope,
            driver = JdbcSqliteDriver("jdbc:sqlite:$dbFilename", Properties(), schema)
        )
        setupSqliteBinding()
        return this.driver as PsSqlDriver
    }

    public companion object {
        init {
            // There is presumably a better way to load the library from the jar.
            @Suppress("UnsafeDynamicallyLoadedCode")
            System.load(OSXLibraryLoader().loadLibraryFromResources())
        }
    }
}