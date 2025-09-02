package com.powersync

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSBundle
import kotlin.getValue

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
public actual class DatabaseDriverFactory {
    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String = appleDefaultDatabasePath(dbFilename)
}

public actual fun BundledSQLiteDriver.addPowerSyncExtension() {
    addExtension(powerSyncExtensionPath, "sqlite3_powersync_init")
}
