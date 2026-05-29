package com.powersync.db.internal

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor
import com.powersync.db.driver.SQLiteConnectionLease

public interface PowerSyncTransaction : ConnectionContext

internal abstract class BasePowerSyncTransactionImpl(
    protected val lease: SQLiteConnectionLease,
) : PowerSyncTransaction,
    ConnectionContext {
    protected val delegate = lease.asContext()

    private suspend fun checkInTransaction() {
        if (!lease.isInTransaction()) {
            throw PowerSyncException("Tried executing statement on a transaction that has been rolled back", cause = null)
        }
    }

    override suspend fun executeAsync(
        sql: String,
        parameters: List<Any?>?,
    ): Long {
        checkInTransaction()
        return delegate.executeAsync(sql, parameters)
    }

    override suspend fun <RowType : Any> getOptionalAsync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType? {
        checkInTransaction()
        return delegate.getOptionalAsync(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> getAllAsync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType> {
        checkInTransaction()
        return delegate.getAllAsync(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> getAsync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType {
        checkInTransaction()
        return delegate.getAsync(sql, parameters, mapper)
    }
}

internal expect fun createTransactionImpl(original: SQLiteConnectionLease): PowerSyncTransaction

@OptIn(ExperimentalPowerSyncAPI::class)
internal suspend fun <T> SQLiteConnectionLease.runTransaction(cb: suspend (PowerSyncTransaction) -> T): T {
    execSQL("BEGIN")
    return try {
        val result = cb(createTransactionImpl(this))

        check(isInTransaction())
        execSQL("COMMIT")
        result
    } catch (e: Throwable) {
        if (isInTransaction()) {
            execSQL("ROLLBACK")
        }

        throw e
    }
}
