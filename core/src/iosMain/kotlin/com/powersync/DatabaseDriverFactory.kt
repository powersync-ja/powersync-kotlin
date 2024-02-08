package com.powersync

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.powersync.db.internal.PsInternalSchema
import com.powersync.sqlite.core.init_powersync_sqlite_extension
import kotlinx.cinterop.ExperimentalForeignApi

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class)
actual class DatabaseDriverFactory {

    init {
        init_powersync_sqlite_extension()
    }

    actual fun createDriver(
        dbFilename: String
    ): SqlDriver {
        return NativeSqliteDriver(PsInternalSchema.synchronous(), dbFilename)
    }
}