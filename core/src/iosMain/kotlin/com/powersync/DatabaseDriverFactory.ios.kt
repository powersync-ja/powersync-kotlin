package com.powersync

import app.cash.sqldelight.db.QueryResult
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConfiguration.Logging
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.NO_VERSION_CHECK
import co.touchlab.sqliter.interop.Logger
import co.touchlab.sqliter.interop.SqliteErrorType
import co.touchlab.sqliter.sqlite3.sqlite3_commit_hook
import co.touchlab.sqliter.sqlite3.sqlite3_enable_load_extension
import co.touchlab.sqliter.sqlite3.sqlite3_load_extension
import co.touchlab.sqliter.sqlite3.sqlite3_rollback_hook
import co.touchlab.sqliter.sqlite3.sqlite3_update_hook
import com.powersync.db.internal.InternalSchema
import com.powersync.persistence.driver.NativeSqliteDriver
import com.powersync.persistence.driver.wrapConnection
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import platform.Foundation.NSBundle

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class)
public actual class DatabaseDriverFactory {
    internal actual fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
        dbDirectory: String?,
        readOnly: Boolean,
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

        // Create a deferred driver reference for hook registrations
        // This must exist before we create the driver since we require
        // a pointer for C hooks
        val deferredDriver = DeferredDriver()

        val driver =
            PsSqlDriver(
                driver =
                    NativeSqliteDriver(
                        configuration =
                            DatabaseConfiguration(
                                name = dbFilename,
                                version =
                                    if (!readOnly) {
                                        schema.version.toInt()
                                    } else {
                                        // Don't do migrations on read only connections
                                        NO_VERSION_CHECK
                                    },
                                create = { connection ->
                                    wrapConnection(connection) {
                                        schema.create(
                                            it,
                                        )
                                    }
                                },
                                loggingConfig = Logging(logger = sqlLogger),
                                lifecycleConfig =
                                    DatabaseConfiguration.Lifecycle(
                                        onCreateConnection = { connection ->
                                            setupSqliteBinding(connection, deferredDriver)
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

        // The iOS driver implementation generates 1 write and 1 read connection internally
        // It uses the read connection for all queries and the write connection for all
        // execute statements. Unfortunately the driver does not seem to respond to query
        // calls if the read connection count is set to zero.
        // We'd like to ensure a driver is set to read-only. Ideally we could do this in the
        // onCreateConnection lifecycle hook, but this runs before driver internal migrations.
        // Setting the connection to read only there breaks migrations.
        // We explicitly execute this pragma to reflect and guard the "write" connection.
        // The read connection already has this set.
        if (readOnly) {
            driver.execute("PRAGMA query_only=true")
        }

        // Ensure internal read pool has created a connection at this point. This makes connection
        // initialization a bit more deterministic.
        driver.executeQuery(
            identifier = null,
            sql = "SELECT 1",
            mapper = { QueryResult.Value(it.getLong(0)) },
            parameters = 0,
        )

        deferredDriver.setDriver(driver)

        return driver
    }

    private fun setupSqliteBinding(
        connection: DatabaseConnection,
        driver: DeferredDriver,
    ) {
        val ptr = connection.getDbPointer().getPointer(MemScope())
        val extensionPath = powerSyncExtensionPath

        // Enable extension loading
        // We don't disable this after the fact, this should allow users to load their own extensions
        // in future.
        val enableResult = sqlite3_enable_load_extension(ptr, 1)
        if (enableResult != SqliteErrorType.SQLITE_OK.code) {
            throw PowerSyncException(
                "Could not dynamically load the PowerSync SQLite core extension",
                cause =
                    Exception(
                        "Call to sqlite3_enable_load_extension failed",
                    ),
            )
        }

        // A place to store a potential error message response
        val errMsg = nativeHeap.alloc<CPointerVar<ByteVar>>()
        val result =
            sqlite3_load_extension(ptr, extensionPath, "sqlite3_powersync_init", errMsg.ptr)
        if (result != SqliteErrorType.SQLITE_OK.code) {
            val errorMessage = errMsg.value?.toKString() ?: "Unknown error"
            throw PowerSyncException(
                "Could not load the PowerSync SQLite core extension",
                cause =
                    Exception(
                        "Calling sqlite3_load_extension failed with error: $errorMessage",
                    ),
            )
        }

        val driverRef = StableRef.create(driver)

        sqlite3_update_hook(
            ptr,
            staticCFunction { usrPtr, updateType, dbName, tableName, rowId ->
                usrPtr!!
                    .asStableRef<DeferredDriver>()
                    .get()
                    .updateTableHook(tableName!!.toKString())
            },
            driverRef.asCPointer(),
        )

        sqlite3_commit_hook(
            ptr,
            staticCFunction { usrPtr ->
                usrPtr!!.asStableRef<DeferredDriver>().get().onTransactionCommit(true)
                0
            },
            driverRef.asCPointer(),
        )

        sqlite3_rollback_hook(
            ptr,
            staticCFunction { usrPtr ->
                usrPtr!!.asStableRef<DeferredDriver>().get().onTransactionCommit(false)
            },
            driverRef.asCPointer(),
        )
    }

    private fun deregisterSqliteBinding(connection: DatabaseConnection) {
        val basePtr = connection.getDbPointer().getPointer(MemScope())

        sqlite3_update_hook(
            basePtr,
            null,
            null,
        )
    }

    internal companion object {
        internal val powerSyncExtensionPath by lazy {
            // Try and find the bundle path for the SQLite core extension.
            val bundlePath =
                NSBundle.bundleWithIdentifier("co.powersync.sqlitecore")?.bundlePath
                    ?: // The bundle is not installed in the project
                    throw PowerSyncException(
                        "Please install the PowerSync SQLite core extension",
                        cause = Exception("The `co.powersync.sqlitecore` bundle could not be found in the project."),
                    )

            // Construct full path to the shared library inside the bundle
            bundlePath.let { "$it/powersync-sqlite-core" }
        }
    }
}
