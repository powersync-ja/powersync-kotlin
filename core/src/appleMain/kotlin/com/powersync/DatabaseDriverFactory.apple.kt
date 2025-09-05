package com.powersync

import kotlinx.cinterop.UnsafeNumber
import kotlinx.io.files.FileSystem
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.getValue

@OptIn(UnsafeNumber::class)
internal fun appleDefaultDatabasePath(dbFilename: String): String {
    // This needs to be compatible with https://github.com/touchlab/SQLiter/blob/a37bbe7e9c65e6a5a94c5bfcaccdaae55ad2bac9/sqliter-driver/src/appleMain/kotlin/co/touchlab/sqliter/DatabaseFileContext.kt#L36-L51
    val paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
    val documentsDirectory = paths[0] as String

    val databaseDirectory = "$documentsDirectory/databases"

    val fileManager = NSFileManager.defaultManager()

    if (!fileManager.fileExistsAtPath(databaseDirectory)) {
        fileManager.createDirectoryAtPath(databaseDirectory, true, null, null)
    }; // Create folder

    return "$databaseDirectory/$dbFilename"
}

internal val powerSyncExtensionPath: String by lazy {
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
