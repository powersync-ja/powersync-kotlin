package com.powersync

import androidx.sqlite.SQLiteConnection
import com.powersync.DatabaseDriverFactory.Companion.powerSyncExtensionPath
import com.powersync.internal.driver.ConnectionListener
import com.powersync.internal.driver.NativeConnection
import com.powersync.internal.driver.NativeDriver
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.io.files.Path
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import sqlite3.SQLITE_OK
import sqlite3.sqlite3_enable_load_extension
import sqlite3.sqlite3_load_extension

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@OptIn(ExperimentalForeignApi::class)
public actual class DatabaseDriverFactory {
    internal actual fun openDatabase(
        dbFilename: String,
        dbDirectory: String?,
        readOnly: Boolean,
        listener: ConnectionListener?
    ): SQLiteConnection {
        val directory = dbDirectory ?: defaultDatabaseDirectory()
        val path = Path(directory, dbFilename).toString()
        val db = NativeDriver().openNativeDatabase(path, readOnly, listener)

        db.loadPowerSyncSqliteCoreExtension()
        return db
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

        @OptIn(UnsafeNumber::class)
        private fun defaultDatabaseDirectory(search: String = "databases"): String {
            // This needs to be compatible with https://github.com/touchlab/SQLiter/blob/a37bbe7e9c65e6a5a94c5bfcaccdaae55ad2bac9/sqliter-driver/src/appleMain/kotlin/co/touchlab/sqliter/DatabaseFileContext.kt#L36-L51
            val paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true);
            val documentsDirectory = paths[0] as String;

            val databaseDirectory = "$documentsDirectory/$search"

            val fileManager = NSFileManager.defaultManager()

            if (!fileManager.fileExistsAtPath(databaseDirectory))
                fileManager.createDirectoryAtPath(databaseDirectory, true, null, null); //Create folder

            return databaseDirectory
        }
    }
}

internal fun NativeConnection.loadPowerSyncSqliteCoreExtensionDynamically() {
    val ptr = sqlite.getPointer(MemScope())
    val extensionPath = powerSyncExtensionPath

    // Enable extension loading
    // We don't disable this after the fact, this should allow users to load their own extensions
    // in future.
    val enableResult = sqlite3_enable_load_extension(ptr, 1)
    if (enableResult != SQLITE_OK) {
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
    val resultingError = errMsg.value
    nativeHeap.free(errMsg)
    if (result != SQLITE_OK) {
        val errorMessage = resultingError?.toKString() ?: "Unknown error"
        throw PowerSyncException(
            "Could not load the PowerSync SQLite core extension",
            cause =
                Exception(
                    "Calling sqlite3_load_extension failed with error: $errorMessage",
                ),
        )
    }
}

internal expect fun NativeConnection.loadPowerSyncSqliteCoreExtension()
