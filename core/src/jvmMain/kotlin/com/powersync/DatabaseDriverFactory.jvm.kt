package com.powersync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "SqlNoDataSourceInspection")
public actual class DatabaseDriverFactory: PersistentDriverFactory {
    actual override val platform: PowerSyncPlatform
        get() = BuiltinPlatform

    private val driver = BundledSQLiteDriver().also { it.addPowerSyncExtension() }

    actual override fun resolveDefaultDatabasePath(dbFilename: String): String = dbFilename

    actual override fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection = driver.open(path, openFlags)
}

@OptIn(ExperimentalPowerSyncAPI::class)
public fun BundledSQLiteDriver.addPowerSyncExtension() {
    addExtension(resolvePowerSyncLoadableExtensionPath()!!, "sqlite3_powersync_init")
}

internal actual fun openInMemoryConnection(): SQLiteConnection = DatabaseDriverFactory().openConnection(":memory:", 0x02)
