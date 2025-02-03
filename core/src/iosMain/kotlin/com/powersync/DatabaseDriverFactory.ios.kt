package com.powersync

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConnection
import com.powersync.db.internal.InternalSchema
import com.powersync.persistence.driver.NativeSqliteDriver
import com.powersync.persistence.driver.wrapConnection
import com.powersync.sqlite.core.init_powersync_sqlite_extension
import com.powersync.sqlite.core.sqlite3_commit_hook
import com.powersync.sqlite.core.sqlite3_rollback_hook
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
public actual class DatabaseDriverFactory {
    private var driver: PsSqlDriver? = null

    init {
        init_powersync_sqlite_extension()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    private fun updateTableHook(
        opType: Int,
        databaseName: String,
        tableName: String,
        rowId: Long,
    ) {
        driver?.updateTable(tableName)
    }

    private fun onTransactionCommit(success: Boolean) {
        driver?.also { driver ->
            if (success) {
                driver.fireTableUpdates()
            } else {
                driver.clearTableUpdates()
            }
        }
    }

    internal actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
    ): PsSqlDriver {
        val schema = InternalSchema
        this.driver =
            PsSqlDriver(
                scope = scope,
                driver =
                    NativeSqliteDriver(
                        configuration =
                            DatabaseConfiguration(
                                name = dbFilename,
                                version = schema.version.toInt(),
                                create = { connection -> wrapConnection(connection) { schema.create(it) } },
                                lifecycleConfig =
                                    DatabaseConfiguration.Lifecycle(
                                        onCreateConnection = { connection ->
                                            setupSqliteBinding(connection)
                                            wrapConnection(connection) { driver ->
                                                schema.create(driver)
                                            }
                                        },
                                        onCloseConnection = { connection ->
                                            deregisterSqliteBinding(connection)
                                        },
                                    ),
                            ),
                    ),
            )
        return this.driver as PsSqlDriver
    }

    private fun setupSqliteBinding(connection: DatabaseConnection) {
        val ptr = connection.getDbPointer().getPointer(MemScope())

        // Register the update hook
        sqlite3_update_hook(
            ptr,
            staticCFunction { usrPtr, updateType, dbName, tableName, rowId ->
                val callback =
                    usrPtr!!
                        .asStableRef<(Int, String, String, Long) -> Unit>()
                        .get()
                callback(
                    updateType,
                    dbName!!.toKString(),
                    tableName!!.toKString(),
                    rowId,
                )
            },
            StableRef.create(::updateTableHook).asCPointer(),
        )

        // Register transaction hooks
        sqlite3_commit_hook(
            ptr,
            staticCFunction { usrPtr ->
                val callback = usrPtr!!.asStableRef<(Boolean) -> Unit>().get()
                callback(true)
                0
            },
            StableRef.create(::onTransactionCommit).asCPointer(),
        )
        sqlite3_rollback_hook(
            ptr,
            staticCFunction { usrPtr ->
                val callback = usrPtr!!.asStableRef<(Boolean) -> Unit>().get()
                callback(false)
            },
            StableRef.create(::onTransactionCommit).asCPointer(),
        )
    }

    private fun deregisterSqliteBinding(connection: DatabaseConnection) {
        val ptr = connection.getDbPointer().getPointer(MemScope())
        sqlite3_update_hook(
            ptr,
            null,
            null,
        )
    }
}
