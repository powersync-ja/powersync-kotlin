package co.powersync.db

import app.cash.sqldelight.db.SqlDriver

import co.powersync.Closeable
import co.powersync.sync.SyncStatus

/**
 * A PowerSync managed database.
 *
 * Use one instance per database file.
 *
 * Use [PowerSyncDatabase.connect] to connect to the PowerSync service, to keep the local database in sync with the remote database.
 *
 * All changes to local tables are automatically recorded, whether connected or not. Once connected, the changes are uploaded.
 */

interface PowerSyncDatabaseConfig : DriverOptions {
    val driverFactory: DatabaseDriverFactory
}

open class PowerSyncDatabase (config: PowerSyncDatabaseConfig
) : Closeable {
    override var closed: Boolean = false

    private val driver: SqlDriver
    private val database: PsDatabase

    /**
     * The current sync status.
     */
    val currentStatus: SyncStatus

    init {
        this.driver = config.driverFactory.createDriver(config)
        this.database = PsDatabase(driver)
        this.currentStatus = SyncStatus()
    }

    companion object {
    }

    override suspend fun close() {
        closed = true
    }
}