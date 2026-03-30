package com.powersync

import com.powersync.internal.core_extension.sqlite3_powersync_init
import com.powersync.internal.sqlite3.sqlite3_auto_extension
import kotlinx.cinterop.staticCFunction

private val didLoadExtension by lazy {
    val rc = sqlite3_auto_extension(staticCFunction { db, errMsg, api ->
        return@staticCFunction sqlite3_powersync_init(db, errMsg, api)
    })
    if (rc != 0) {
        throw PowerSyncException(
            "Could not load the PowerSync SQLite core extension",
            cause =
                Exception(
                    "Calling powersync_init_static returned result code $rc",
                ),
        )
    }

    true
}

@Throws(exceptionClasses = [PowerSyncException::class])
public actual fun resolvePowerSyncLoadableExtensionPath(): String? {
    didLoadExtension
    return null
}
