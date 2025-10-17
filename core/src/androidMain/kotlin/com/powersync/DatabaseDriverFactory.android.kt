package com.powersync

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.Throws

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class DatabaseDriverFactory(
    private val context: Context,
): PersistentConnectionFactory, DriverBasedInMemoryFactory<BundledSQLiteDriver>(newDriver()) {
    actual override fun resolveDefaultDatabasePath(dbFilename: String): String = context.getDatabasePath(dbFilename).path

    actual override fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection = driver.open(path, openFlags)
}

private fun newDriver() = BundledSQLiteDriver().also { it.addPowerSyncExtension() }

public fun BundledSQLiteDriver.addPowerSyncExtension() {
    addExtension("libpowersync.so", "sqlite3_powersync_init")
}

internal actual val inMemoryDriver: InMemoryConnectionFactory = DriverBasedInMemoryFactory(newDriver())
