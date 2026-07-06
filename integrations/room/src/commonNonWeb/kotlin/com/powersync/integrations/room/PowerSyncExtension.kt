package com.powersync.integrations.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.powersync.resolvePowerSyncLoadableExtensionPath

/**
 * Configures this driver to load the PowerSync core SQLite extension on connections it opens.
 */
public fun BundledSQLiteDriver.loadPowerSyncExtension() {
    resolvePowerSyncLoadableExtensionPath()?.let {
        addExtension(it, "sqlite3_powersync_init")
    }
}
