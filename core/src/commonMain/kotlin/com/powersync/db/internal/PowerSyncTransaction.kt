package com.powersync.db.internal

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor

public interface PowerSyncTransaction : ConnectionContext

internal class PowerSyncTransactionImpl(
    override val rawConnection: SQLiteConnection,
) : PowerSyncTransaction,
    ConnectionContext {
    private val delegate = ConnectionContextImplementation(rawConnection)

    private fun checkInTransaction() {
        if (!rawConnection.inTransaction()) {
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

internal inline fun <T> SQLiteConnection.runTransaction(cb: (PowerSyncTransaction) -> T): T {
    execSQL("BEGIN")
    var didComplete = false
    return try {
        val result = cb(PowerSyncTransactionImpl(this))
        didComplete = true

        check(inTransaction())
        execSQL("COMMIT")
        result
    } catch (e: Throwable) {
        if (!didComplete && inTransaction()) {
            execSQL("ROLLBACK")
        }

        throw e
    }
}
