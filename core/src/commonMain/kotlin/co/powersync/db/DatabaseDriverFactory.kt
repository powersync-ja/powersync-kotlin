package co.powersync.db

import app.cash.sqldelight.db.SqlDriver
import co.powersync.db.schema.Schema


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class DatabaseDriverFactory {
    fun createDriver(
        schema: Schema,
        dbFilename: String
    ): SqlDriver
}
