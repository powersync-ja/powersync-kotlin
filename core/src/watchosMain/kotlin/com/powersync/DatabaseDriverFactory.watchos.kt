package com.powersync

import com.powersync.internal.driver.NativeConnection
import com.powersync.static.powersync_init_static

internal actual fun NativeConnection.loadPowerSyncSqliteCoreExtension() {
    val rc = powersync_init_static()
    if (rc != 0) {
        throw PowerSyncException(
            "Could not load the PowerSync SQLite core extension",
            cause =
                Exception(
                    "Calling powersync_init_static returned result code $rc",
                ),
        )
    }
}
