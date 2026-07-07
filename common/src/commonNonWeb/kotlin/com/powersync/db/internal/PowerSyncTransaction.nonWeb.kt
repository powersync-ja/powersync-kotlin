package com.powersync.db.internal

import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor
import com.powersync.db.driver.SQLiteConnectionLease

internal actual fun createTransactionImpl(original: SQLiteConnectionLease): PowerSyncTransaction = TransactionImpl(original)

private class TransactionImpl(
    lease: SQLiteConnectionLease,
) : BasePowerSyncTransactionImpl(lease) {
    private fun checkInTransaction() {
        if (!lease.isInTransactionSync()) {
            throw PowerSyncException(
                "Tried executing statement on a transaction that has been rolled back",
                cause = null,
            )
        }
    }

    override fun execute(
        sql: String,
        parameters: List<Any?>?,
    ): Long {
        checkInTransaction()
        return delegate.execute(sql, parameters)
    }

    override fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType? {
        checkInTransaction()
        return delegate.getOptional(sql, parameters, mapper)
    }

    override fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType> {
        checkInTransaction()
        return delegate.getAll(sql, parameters, mapper)
    }

    override fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType {
        checkInTransaction()
        return delegate.get(sql, parameters, mapper)
    }
}
