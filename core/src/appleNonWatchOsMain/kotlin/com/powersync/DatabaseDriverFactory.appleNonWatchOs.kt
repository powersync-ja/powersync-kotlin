package com.powersync

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSBundle
import kotlin.getValue

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
public actual class DatabaseDriverFactory {
    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String {
        return appleDefaultDatabasePath(dbFilename)
    }
}

public actual fun BundledSQLiteDriver.addPowerSyncExtension() {
    addExtension(powerSyncExtensionPath, "sqlite3_powersync_init")
}

private val powerSyncExtensionPath: String by lazy {
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
