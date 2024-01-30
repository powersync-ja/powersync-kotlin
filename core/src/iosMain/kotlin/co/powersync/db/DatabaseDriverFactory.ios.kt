package co.powersync.db

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.powersync.db.schema.Schema
import kotlinx.cinterop.ExperimentalForeignApi
import co.powersync.sqlite.core.init_powersync_sqlite_extension

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class)
actual class DatabaseDriverFactory {

    init {
        init_powersync_sqlite_extension()
    }

    actual fun createDriver(
        schema: Schema,
        dbFilename: String
    ): SqlDriver {
        return NativeSqliteDriver(PsDatabase.Schema.synchronous(), dbFilename)
    }
}