package co.powersync.db

import app.cash.sqldelight.db.SqlDriver

import co.powersync.Closeable
import co.powersync.db.schema.Schema

/**
 * A PowerSync managed database.
 *
 * Use one instance per database file.
 *
 * Use [PowerSyncDatabase.connect] to connect to the PowerSync service, to keep the local database in sync with the remote database.
 *
 * All changes to local tables are automatically recorded, whether connected or not. Once connected, the changes are uploaded.
 */

open class PowerSyncDatabase (
    val schema: Schema,
    val path: String,
) : Closeable {
    override var closed: Boolean = false

    private val driver: SqlDriver = TODO()

    companion object {
    }

    override suspend fun close() {
        closed = true
    }
}