package com.powersync

import app.cash.sqldelight.db.SqlDriver
import com.powersync.db.schema.Schema

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class DatabaseDriverFactory {

    fun createDriver(
        dbFilename: String
    ): PsSqliteDriver
}