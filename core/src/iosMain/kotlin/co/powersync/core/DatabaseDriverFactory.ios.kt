package co.powersync.core

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
//import co.powersync.core.sqlite.init_powersync_sqlite_plugin
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual class DatabaseDriverFactory {

//    init {
//        init_powersync_sqlite_plugin()
//    }

    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(AppDatabase.Schema, "test.db")
    }
}