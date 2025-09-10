package com.powersync.db.internal

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor
import com.powersync.db.driver.SQLiteConnectionLease

@Deprecated("Use suspending callback instead")
public interface PowerSyncTransaction : ConnectionContext

@ExperimentalPowerSyncAPI
@Deprecated("Use suspending callback instead")
internal class PowerSyncTransactionImpl(
    val lease: SQLiteConnectionLease,
) : PowerSyncTransaction,
    ConnectionContext {
    private val delegate = ConnectionContextImplementation(lease)

    private fun checkInTransaction() {
        if (!lease.isInTransactionSync()) {
            throw PowerSyncException("Tried executing statement on a transaction that has been rolled back", cause = null)
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

@ExperimentalPowerSyncAPI
internal suspend fun <T> SQLiteConnectionLease.runTransaction(cb: suspend (PowerSyncTransactionImpl) -> T): T {
    execSQL("BEGIN")
    return try {
        val result = cb(PowerSyncTransactionImpl(this))

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
