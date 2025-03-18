package com.powersync

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConfiguration.Logging
import co.touchlab.sqliter.DatabaseConnection
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
import kotlinx.cinterop.reinterpret
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
                                version = schema.version.toInt(),
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

        deferredDriver.setDriver(driver)

        return driver
    }

    private fun setupSqliteBinding(
        connection: DatabaseConnection,
        driver: DeferredDriver,
    ) {
        val ptr = connection.getDbPointer().getPointer(MemScope())
        // Try and find the bundle path for the SQLite core extension.
        val bundlePath =
            NSBundle.bundleWithIdentifier("co.powersync.sqlitecore")?.bundlePath
                ?: // The bundle is not installed in the project
                throw PowerSyncException(
                    "Please install the PowerSync SQLite core extension",
                    cause = Exception("The `co.powersync.sqlitecore` bundle could not be found in the project."),
                )

        // Construct full path to the shared library inside the bundle
        val extensionPath = bundlePath.let { "$it/powersync-sqlite-core" }

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
            basePtr.reinterpret(),
            null,
            null,
        )
    }
}
