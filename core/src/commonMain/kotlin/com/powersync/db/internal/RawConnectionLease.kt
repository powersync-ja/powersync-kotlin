package com.powersync.db.internal

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * A temporary view / lease of an inner [SQLiteConnection] managed by the PowerSync SDK.
 */
internal class RawConnectionLease(
    private val connection: SQLiteConnection,
    var completed: Boolean = false,
) : SQLiteConnection {
    private fun checkNotCompleted() {
        check(!completed) { "Connection lease already closed" }
    }

    override fun inTransaction(): Boolean {
        checkNotCompleted()
        return connection.inTransaction()
    }

    override fun prepare(sql: String): SQLiteStatement {
        checkNotCompleted()
        return connection.prepare(sql)
    }

    override fun close() {
        // Note: This is a lease, don't close the underlying connection.
    }
}
