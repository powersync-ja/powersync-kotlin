package com.powersync.db.internal

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import com.powersync.db.QueryRunner
import com.powersync.db.SqlCursor
import com.powersync.db.driver.SQLiteConnectionLease

public interface PowerSyncTransaction : ConnectionContext

@ExperimentalPowerSyncAPI
internal class PowerSyncTransactionImpl(
    lease: SQLiteConnectionLease,
) : PowerSyncTransaction, BaseConnectionContextImplementation() {
    override val async = AsyncPowerSyncTransactionImpl(lease)
}

@OptIn(ExperimentalPowerSyncAPI::class)
internal class AsyncPowerSyncTransactionImpl(
    private val lease: SQLiteConnectionLease
): QueryRunner {

    private val delegate = ContextQueryRunner(lease)

    private suspend fun checkInTransaction() {
        if (!lease.isInTransaction()) {
            throw PowerSyncException("Tried executing statement on a transaction that has been rolled back", cause = null)
        }
    }

    override suspend fun execute(
        sql: String,
        parameters: List<Any?>?
    ): Long {
        checkInTransaction()
        return delegate.execute(sql, parameters)
    }

    override suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): RowType {
        checkInTransaction()
        return delegate.get(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): List<RowType> {
        checkInTransaction()
        return delegate.getAll(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): RowType? {
        checkInTransaction()
        return delegate.getOptional(sql, parameters, mapper)
    }
}

@ExperimentalPowerSyncAPI
internal suspend fun <T> SQLiteConnectionLease.runTransaction(cb: suspend (PowerSyncTransaction) -> T): T {
    execSQL("BEGIN")
    var didComplete = false
    return try {
        val result = cb(PowerSyncTransactionImpl(this))
        didComplete = true

        check(isInTransaction())
        execSQL("COMMIT")
        result
    } catch (e: Throwable) {
        if (!didComplete && isInTransaction()) {
            execSQL("ROLLBACK")
        }

        throw e
    }
}
