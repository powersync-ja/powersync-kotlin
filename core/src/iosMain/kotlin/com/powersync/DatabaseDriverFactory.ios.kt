package com.powersync

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConfiguration.Logging
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.interop.Logger
import co.touchlab.sqliter.interop.SqliteErrorType
import com.powersync.sqlite3.sqlite3_commit_hook

import com.powersync.sqlite3.sqlite3_load_extension
import com.powersync.sqlite3.sqlite3_rollback_hook
import com.powersync.sqlite3.sqlite3_update_hook
import com.powersync.db.internal.InternalSchema
import com.powersync.persistence.driver.NativeSqliteDriver
import com.powersync.persistence.driver.wrapConnection
import com.powersync.sqlite3.sqlite3
import com.powersync.sqlite3.sqlite3_enable_load_extension

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
    private var driver: PsSqlDriver? = null

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
        return this.driver as PsSqlDriver
    }

    private fun setupSqliteBinding(connection: DatabaseConnection) {
        val baseptr = connection.getDbPointer().getPointer(MemScope())
        // Register the update hook
        val bundlePath =  NSBundle.bundleWithIdentifier("co.powersync.sqlitecore")?.bundlePath
        println("bundle path is $bundlePath")

        // Construct full path to the shared library inside the bundle
        val extensionPath = bundlePath?.let { "$it/powersync-sqlite-core" }
        val ptr = baseptr.reinterpret<sqlite3>()
//         Enable extension loading
        val enableResult = sqlite3_enable_load_extension(ptr, 1)
        if (enableResult != SqliteErrorType.SQLITE_OK.code) {
            println("Failed to enable SQLite extensions")
            return
        }

        println("Extension path $extensionPath")
        if (extensionPath != null) {
                val errMsg = nativeHeap.alloc<CPointerVar<ByteVar>>()
//                val errMsg = alloc<CPointerVar<ByteVar>>()
                println("attempting load")
                val result = sqlite3_load_extension(ptr, extensionPath, "sqlite3_powersync_init", errMsg.ptr)
                if (result != SqliteErrorType.SQLITE_OK.code) {
                    println("getting error")
                    val errorMessage = errMsg.value?.toKString() ?: "Unknown error"
                    println("Failed to load SQLite extension: $errorMessage")
                } else {
                    println("Successfully loaded SQLite extension: $extensionPath")
                }

        } else {
            println("Failed to determine SQLite extension path.")
        }

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
        val baseptr = connection.getDbPointer().getPointer(MemScope())
        val ptr = baseptr.reinterpret<sqlite3>()
        sqlite3_update_hook(
            ptr,
            null,
            null,
        )
    }
}
