package com.powersync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.powersync.db.runWrapped

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "SqlNoDataSourceInspection")
public actual class DatabaseDriverFactory {
    private val driver = BundledSQLiteDriver().also { it.addPowerSyncExtension() }

    internal actual fun resolveDefaultDatabasePath(dbFilename: String): String = dbFilename

    internal actual fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection = driver.open(path, openFlags)
}

public fun BundledSQLiteDriver.addPowerSyncExtension() {
    addExtension(powersyncExtension, "sqlite3_powersync_init")
}

private val powersyncExtension: String by lazy { extractLib("powersync") }

@ExperimentalPowerSyncAPI
@Throws(PowerSyncException::class)
public actual fun resolvePowerSyncLoadableExtensionPath(): String? = runWrapped { powersyncExtension }

internal actual fun openInMemoryConnection(): SQLiteConnection = DatabaseDriverFactory().openConnection(":memory:", 0x02)
