package com.powersync.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.powersync.db.schema.Schema
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(
        schema: Schema,
        dbFilename: String
    ): SqlDriver {
        return AndroidSqliteDriver(
            PsDatabase.Schema.synchronous(),
            context,
            dbFilename,
            factory = RequerySQLiteOpenHelperFactory(
                listOf(RequerySQLiteOpenHelperFactory.ConfigurationOptions { config ->
                    config.customExtensions.add(
                        SQLiteCustomExtension(
                            "libpowersync",
                            "sqlite3_powersync_init"
                        )
                    )
                    config
                })
            )
        )
    }

}