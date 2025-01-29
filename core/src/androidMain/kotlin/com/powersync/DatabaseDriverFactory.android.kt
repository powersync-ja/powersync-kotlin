package com.powersync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.powersync.db.internal.InternalSchema
import com.powersync.persistence.driver.AndroidSqliteDriver
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension
import kotlinx.coroutines.CoroutineScope

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class DatabaseDriverFactory(
    private val context: Context,
) {
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

    internal actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
    ): PsSqlDriver {
        val schema = InternalSchema

        this.driver =
            PsSqlDriver(
                scope = scope,
                driver =
                    AndroidSqliteDriver(
                        context = context,
                        schema = schema,
                        name = dbFilename,
                        factory =
                            RequerySQLiteOpenHelperFactory(
                                listOf(
                                    RequerySQLiteOpenHelperFactory.ConfigurationOptions { config ->
                                        config.customExtensions.add(
                                            SQLiteCustomExtension(
                                                "libpowersync",
                                                "sqlite3_powersync_init",
                                            ),
                                        )
                                        config.customExtensions.add(
                                            SQLiteCustomExtension(
                                                "libpowersync-sqlite",
                                                "powersync_init",
                                            ),
                                        )
                                        config
                                    },
                                ),
                            ),
                        callback =
                            object : AndroidSqliteDriver.Callback(schema) {
                                override fun onConfigure(db: SupportSQLiteDatabase) {
                                    db.enableWriteAheadLogging()
                                    super.onConfigure(db)
                                }
                            },
                    ),
            )
        setupSqliteBinding()
        return this.driver as PsSqlDriver
    }

    public companion object {
        init {
            System.loadLibrary("powersync-sqlite")
        }
    }
}
