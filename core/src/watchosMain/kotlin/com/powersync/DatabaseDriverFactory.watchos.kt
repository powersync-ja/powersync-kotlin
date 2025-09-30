package com.powersync

import androidx.sqlite.SQLiteConnection
import com.powersync.sqlite.Database
import com.powersync.static.powersync_init_static

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
public actual class DatabaseDriverFactory {
    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String = appleDefaultDatabasePath(dbFilename)

    internal actual fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection {
        didLoadExtension
        return Database.open(path, openFlags)
    }
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

@ExperimentalPowerSyncAPI
@Throws(PowerSyncException::class)
public actual fun resolvePowerSyncLoadableExtensionPath(): String? {
    didLoadExtension
    return null
}

internal actual fun openInMemoryConnection(): SQLiteConnection = DatabaseDriverFactory().openConnection(":memory:", 0x02)
