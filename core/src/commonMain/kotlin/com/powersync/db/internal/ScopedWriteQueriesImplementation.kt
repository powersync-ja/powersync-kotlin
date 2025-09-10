package com.powersync.db.internal

import androidx.sqlite.SQLiteStatement
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import com.powersync.db.ScopedWriteQueries
import com.powersync.db.SqlCursor
import com.powersync.db.StatementBasedCursor
import com.powersync.db.driver.SQLiteConnectionLease

@OptIn(ExperimentalPowerSyncAPI::class)
internal class ScopedWriteQueriesImplementation(
    private val rawConnection: SQLiteConnectionLease,
    private val isTransaction: Boolean = false,
) : ScopedWriteQueries {
    override suspend fun execute(
        sql: String,
        parameters: List<Any?>?,
    ): Long {
        withStatement(sql, parameters) {
            while (it.step()) {
                // Iterate through the statement
            }
        }

        return withStatement("SELECT changes()", null) {
            check(it.step())
            it.getLong(0)
        }
    }

    override suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType = getOptional(sql, parameters, mapper) ?: throw PowerSyncException("get() called with query that returned no rows", null)

    override suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType> =
        withStatement(sql, parameters) { stmt ->
            buildList {
                val cursor = StatementBasedCursor(stmt)
                while (stmt.step()) {
                    add(mapper(cursor))
                }
            }
        }

    override suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType? =
        withStatement(sql, parameters) { stmt ->
            if (stmt.step()) {
                mapper(StatementBasedCursor(stmt))
            } else {
                null
            }
        }

    private suspend fun <T> withStatement(
        sql: String,
        parameters: List<Any?>?,
        block: (SQLiteStatement) -> T,
    ): T {
        if (isTransaction) {
            if (!rawConnection.isInTransactionSync()) {
                throw PowerSyncException("Tried executing statement on a transaction that has been rolled back", cause = null)
            }
        }

        return rawConnection.usePrepared(sql) { stmt ->
            stmt.bind(parameters)
            block(stmt)
        }
    }
}
