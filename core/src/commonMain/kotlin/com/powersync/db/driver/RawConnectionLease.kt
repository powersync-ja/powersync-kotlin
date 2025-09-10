package com.powersync.db.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.db.runWrapped

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

    override suspend fun isInTransaction(): Boolean = isInTransactionSync()

    override fun isInTransactionSync(): Boolean {
        checkNotCompleted()
        return runWrapped {
            connection.inTransaction()
        }
    }

    override suspend fun <R> usePrepared(
        sql: String,
        block: (SQLiteStatement) -> R,
    ): R = usePreparedSync(sql, block)

    override fun <R> usePreparedSync(
        sql: String,
        block: (SQLiteStatement) -> R,
    ): R {
        checkNotCompleted()
        return runWrapped {
            connection.prepare(sql).use(block)
        }
    }
}
