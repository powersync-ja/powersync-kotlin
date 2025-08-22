package com.powersync.db.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.powersync.ExperimentalPowerSyncAPI

/**
 * A temporary view / lease of an inner [androidx.sqlite.SQLiteConnection] managed by the PowerSync
 * SDK.
 */
@OptIn(ExperimentalPowerSyncAPI::class)
internal class RawConnectionLease(
    private val connection: SQLiteConnection,
) : SQLiteConnectionLease {
    private var isCompleted = false

    private fun checkNotCompleted() {
        check(!isCompleted) { "Connection lease already closed" }
    }

    override suspend fun isInTransaction(): Boolean {
        return isInTransactionSync()
    }

    override fun isInTransactionSync(): Boolean {
        checkNotCompleted()
        return connection.inTransaction()
    }

    override suspend fun <R> usePrepared(
        sql: String,
        block: (SQLiteStatement) -> R
    ): R {
        return usePreparedSync(sql, block)
    }

    override fun <R> usePreparedSync(sql: String, block: (SQLiteStatement) -> R): R {
        checkNotCompleted()
        return connection.prepare(sql).use(block)
    }
}
