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

    internal companion object {
        fun newDriver(): BundledSQLiteDriver {
            return BundledSQLiteDriver().also { addPowerSyncExtension(it) }
        }

        @OptIn(ExperimentalPowerSyncAPI::class)
        fun addPowerSyncExtension(driver: BundledSQLiteDriver) {
            driver.addExtension(resolvePowerSyncLoadableExtensionPath()!!, "sqlite3_powersync_init")
        }
    }
}

@OptIn(ExperimentalPowerSyncAPI::class)
public fun BundledSQLiteDriver.addPowerSyncExtension() {
    DatabaseDriverFactory.addPowerSyncExtension(this)
}

internal actual val inMemoryDriver: InMemoryConnectionFactory = DriverBasedInMemoryFactory(DatabaseDriverFactory.newDriver())
