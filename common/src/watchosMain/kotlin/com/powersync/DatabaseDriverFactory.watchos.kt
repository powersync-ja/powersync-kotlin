package com.powersync

import com.powersync.static.powersync_init_static

private val didLoadExtension by lazy {
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

    true
}

@ExperimentalPowerSyncAPI
@Throws(PowerSyncException::class)
public actual fun resolvePowerSyncLoadableExtensionPath(): String? {
    didLoadExtension
    return null
}
