package com.powersync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.powersync.db.internal.PsInternalSchema
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension
import kotlinx.coroutines.CoroutineScope

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DatabaseDriverFactory(private val context: Context) {
    private var driver: PsSqliteDriver? = null
    private external fun setupSqliteBinding()

    @Suppress("unused")
    private fun onTableUpdate(tableName: String) {
        driver?.updateTableHook(tableName)
    }

    actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
    ): PsSqliteDriver {
        val schema = PsInternalSchema.synchronous()

        this.driver = PsSqliteDriver(scope = scope, driver = AndroidSqliteDriver(
            context = context,
            schema = schema,
            name = dbFilename,
            factory = RequerySQLiteOpenHelperFactory(
                listOf(RequerySQLiteOpenHelperFactory.ConfigurationOptions { config ->
                    config.customExtensions.add(
                        SQLiteCustomExtension(
                            "libpowersync",
                            "sqlite3_powersync_init"
                        )
                    )
                    config.customExtensions.add(
                        SQLiteCustomExtension(
                            "libpowersync-sqlite",
                            "powersync_init"
                        )
                    )
                    config
                })
            ),
            callback = object : AndroidSqliteDriver.Callback(schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.enableWriteAheadLogging()
                    super.onConfigure(db)
                }
            }
        ))
        setupSqliteBinding()
        return this.driver as PsSqliteDriver
    }


    companion object {
        init {
            System.loadLibrary("powersync-sqlite")
        }
    }

}