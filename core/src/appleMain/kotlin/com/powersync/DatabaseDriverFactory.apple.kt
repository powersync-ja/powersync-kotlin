package com.powersync

import kotlinx.cinterop.UnsafeNumber
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

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

    return databaseDirectory
}
