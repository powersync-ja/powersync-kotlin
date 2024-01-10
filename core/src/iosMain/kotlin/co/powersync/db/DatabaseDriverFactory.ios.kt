package co.powersync.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import co.powersync.sqlite.core.init_powersync_sqlite_extension

@OptIn(ExperimentalForeignApi::class)
actual class DatabaseDriverFactory {

    init {
        init_powersync_sqlite_extension()
    }

    actual fun createDriver(options: DriverOptions): SqlDriver {
        return NativeSqliteDriver(PsDatabase.Schema, options.dbFilename)
    }
}