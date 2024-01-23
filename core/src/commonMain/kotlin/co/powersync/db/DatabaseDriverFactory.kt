package co.powersync.db

import app.cash.sqldelight.db.SqlDriver
import co.powersync.db.schema.Schema


expect class DatabaseDriverFactory {
    fun createDriver(
        schema: Schema,
        dbFilename: String
    ): SqlDriver
}
