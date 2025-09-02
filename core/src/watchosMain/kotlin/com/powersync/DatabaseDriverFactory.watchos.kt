package com.powersync

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.powersync.static.powersync_init_static

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
public actual class DatabaseDriverFactory {
    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String = appleDefaultDatabasePath(dbFilename)
}

public actual fun BundledSQLiteDriver.addPowerSyncExtension() {
    didLoadExtension
}

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
