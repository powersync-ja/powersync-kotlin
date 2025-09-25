package com.powersync.pool

import androidx.sqlite.SQLiteStatement
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.sqlite.Database

internal class RawConnectionLease(
    lease: SwiftLeaseAdapter,
) : SQLiteConnectionLease {
    private var isCompleted = false

    private var db = Database(lease.pointer)

    private fun checkNotCompleted() {
        check(!isCompleted) { "Connection lease already closed" }
    }

    override suspend fun isInTransaction(): Boolean = isInTransactionSync()

    override fun isInTransactionSync(): Boolean {
        checkNotCompleted()
        return db.inTransaction()
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
        return db.prepare(sql).use(block)
    }
}
