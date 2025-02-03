package com.powersync

import com.powersync.db.internal.InternalSchema
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "SqlNoDataSourceInspection")
public actual class DatabaseDriverFactory {
    private var driver: PsSqlDriver? = null

    private external fun setupSqliteBinding()

    // Used in native
    @Suppress("unused")
    private fun onTableUpdate(tableName: String) {
        driver?.updateTable(tableName)
    }

    // Used in native
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
            jniExtension to "powersync_init",
        )

        setupSqliteBinding()

        this.driver =
            PsSqlDriver(
                scope = scope,
                driver = driver,
            )

        return this.driver!!
    }

    public companion object {
        private val jniExtension: Path
        private val powersyncExtension: Path

        init {
            val nativeLib = extractLib("powersync-sqlite")
            @Suppress("UnsafeDynamicallyLoadedCode")
            System.load(nativeLib.absolutePathString())
            jniExtension = nativeLib

            powersyncExtension = extractLib("powersync")
        }
    }
}
