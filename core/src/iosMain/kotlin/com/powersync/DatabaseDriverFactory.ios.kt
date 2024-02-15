package com.powersync

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import com.powersync.db.internal.PsInternalSchema
import com.powersync.sqlite.core.init_powersync_sqlite_extension
import com.powersync.sqlite.core.sqlite3_update_hook
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class)
actual class DatabaseDriverFactory {

    init {
        init_powersync_sqlite_extension()
    }

    actual fun createDriver(
        dbFilename: String
    ): SqlDriver {
        val schema = PsInternalSchema.synchronous()
        return NativeSqliteDriver(
            configuration = DatabaseConfiguration(
                name = dbFilename,
                version = schema.version.toInt(),
                create = { connection -> wrapConnection(connection) { schema.create(it) } },
                lifecycleConfig = DatabaseConfiguration.Lifecycle(
                    onCreateConnection = { connection ->
                        wrapConnection(connection) { driver ->
                            val ptr = connection.getDbPointer().getPointer(MemScope())
                            sqlite3_update_hook(
                                ptr,
                                staticCFunction { usrPtr, updateType, dbName, tableName, rowId ->
                                    val callback =
                                        usrPtr!!.asStableRef<(Int, String, String, Long) -> Unit>()
                                            .get()
                                    callback(
                                        updateType,
                                        dbName!!.toKString(),
                                        tableName!!.toKString(),
                                        rowId
                                    )
                                },
                                StableRef.create(driver.updateHook).asCPointer()
                            )
                            schema.create(driver)
                        }
                    },
                    onCloseConnection = { connection ->
                        sqlite3_update_hook(
                            connection.getDbPointer().getPointer(MemScope()),
                            null,
                            null
                        )
                    }
                )
            )
        )
    }
}