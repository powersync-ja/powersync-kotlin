package co.powersync.db

import app.cash.sqldelight.db.SqlDriver
import co.powersync.db.schema.Schema


interface DriverOptions {
    /**
     * Schema used for the local database.
     */
    val schema: Schema
    /**
     * Filename for the database.
     */
    val dbFilename: String
}


expect class DatabaseDriverFactory {
    fun createDriver(options: DriverOptions): SqlDriver
}
