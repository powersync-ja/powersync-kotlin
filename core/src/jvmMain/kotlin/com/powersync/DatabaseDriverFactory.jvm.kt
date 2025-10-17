package com.powersync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "SqlNoDataSourceInspection")
public actual class DatabaseDriverFactory: PersistentConnectionFactory, DriverBasedInMemoryFactory<BundledSQLiteDriver>(newDriver()) {
    actual override fun resolveDefaultDatabasePath(dbFilename: String): String = dbFilename

    actual override fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection = driver.open(path, openFlags)
}

private fun newDriver() = BundledSQLiteDriver().also { it.addPowerSyncExtension() }

@OptIn(ExperimentalPowerSyncAPI::class)
public fun BundledSQLiteDriver.addPowerSyncExtension() {
    addExtension(resolvePowerSyncLoadableExtensionPath()!!, "sqlite3_powersync_init")
}

internal actual val inMemoryDriver: InMemoryConnectionFactory = DriverBasedInMemoryFactory(newDriver())
