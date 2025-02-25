package com.powersync

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConfiguration.Logging
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.interop.Logger
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
    private var readDriver: PsSqlDriver? = null

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
            // Only clear updates on rollback
            // We manually fire updates when a transaction ended
            if (!success) {
                driver.clearTableUpdates()
            }
        }
    }

    internal actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
    ): PsSqlDriver {
        val schema = InternalSchema
        val sqlLogger =
            object : Logger {
                override val eActive: Boolean
                    get() = false
                override val vActive: Boolean
                    get() = false

                override fun eWrite(
                    message: String,
                    exception: Throwable?,
                ) {
                }

                override fun trace(message: String) {}

                override fun vWrite(message: String) {}
            }

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
                                loggingConfig = Logging(logger = sqlLogger),
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

        // Create a separate read-only driver
        this.readDriver =
            PsSqlDriver(
                scope = scope,
                driver =
                    NativeSqliteDriver(
                        configuration =
                            DatabaseConfiguration(
                                name = dbFilename,
                                version = schema.version.toInt(),
                                create = { }, // No need to create schema again
                                loggingConfig = Logging(logger = sqlLogger),
                                lifecycleConfig =
                                    DatabaseConfiguration.Lifecycle(
                                        onCreateConnection = { connection ->
                                            // Set connection to read-only mode
                                            connection.rawExecSql("PRAGMA query_only = 1;")
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

    internal actual fun getReadDriver(): PsSqlDriver = this.readDriver ?: throw IllegalStateException("Read driver not initialized")
}
