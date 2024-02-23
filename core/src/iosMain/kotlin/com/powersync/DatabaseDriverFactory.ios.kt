package com.powersync

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConnection
import com.powersync.db.internal.PsInternalSchema
import com.powersync.sqlite.core.init_powersync_sqlite_extension
import com.powersync.sqlite.core.sqlite3_update_hook
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class)
actual class DatabaseDriverFactory {
    private var driver: PsSqliteDriver? = null

    init {
        init_powersync_sqlite_extension()
    }

    @Suppress("unused")
    private fun updateTableHook(opType: Int, databaseName: String, tableName: String, rowId: Long) {
        driver?.updateTableHook(tableName)
    }

    actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
    ): PsSqliteDriver {
        val schema = PsInternalSchema.synchronous()
        this.driver = PsSqliteDriver(scope = scope, driver = NativeSqliteDriver(
            configuration = DatabaseConfiguration(
                name = dbFilename,
                version = schema.version.toInt(),
                create = { connection -> wrapConnection(connection) { schema.create(it) } },
                lifecycleConfig = DatabaseConfiguration.Lifecycle(
                    onCreateConnection = { connection ->
                        setupSqliteBinding(connection)
                        wrapConnection(connection) { driver ->
                            schema.create(driver)
                        }
                    },
                    onCloseConnection = { connection ->
                        deregisterSqliteBinding(connection)
                    }
                )
            )
        )
        )
        return this.driver as PsSqliteDriver
    }

    private fun setupSqliteBinding(connection: DatabaseConnection) {
        val ptr = connection.getDbPointer().getPointer(MemScope())

        // Register the update hook
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
            StableRef.create(::updateTableHook).asCPointer()
        )

        // Register transaction hooks
    }

    private fun deregisterSqliteBinding(connection: DatabaseConnection) {
        val ptr = connection.getDbPointer().getPointer(MemScope())
        sqlite3_update_hook(
            ptr,
            null,
            null
        )
    }
}

