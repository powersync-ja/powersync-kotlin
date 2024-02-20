package com.powersync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.powersync.db.internal.PsInternalSchema
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DatabaseDriverFactory(private val context: Context) {
    private var driver: SqlDriver? = null
    private external fun setupSqliteUpdateHook()

    @Suppress("unused")
    private fun onUpdate(
        opType: Int,
        databaseName: String,
        tableName: String,
        rowId: Long
    ) {
        println("DatabaseDriverFactory.android table update: $tableName")
        driver?.updateHook(opType, databaseName, tableName, rowId)
    }

    actual fun createDriver(
        dbFilename: String
    ): SqlDriver {
        val schema = PsInternalSchema.synchronous()

        this.driver = AndroidSqliteDriver(
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
        )
        setupSqliteUpdateHook()
        return this.driver as SqlDriver
    }


    companion object {
        init {
            System.loadLibrary("powersync-sqlite")
        }
    }

}