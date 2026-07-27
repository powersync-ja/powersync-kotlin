package com.powersync.db.internal

import androidx.sqlite.SQLiteStatement
import androidx.sqlite.async.step
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor
import com.powersync.db.StatementBasedCursor
import com.powersync.db.driver.SQLiteConnectionLease

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect interface ConnectionContext {
    public suspend fun executeAsync(
        sql: String,
        parameters: List<Any?>? = listOf(),
    ): Long

    public suspend fun <RowType : Any> getOptionalAsync(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType?

    public suspend fun <RowType : Any> getAllAsync(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): List<RowType>

    public suspend fun <RowType : Any> getAsync(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType
}

internal expect fun SQLiteConnectionLease.asContext(): ConnectionContext

internal abstract class BaseConnectionContextImplementation(
    protected val rawConnection: SQLiteConnectionLease,
) : ConnectionContext {
    override suspend fun executeAsync(
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

    override suspend fun <RowType : Any> getOptionalAsync(
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

    override suspend fun <RowType : Any> getAllAsync(
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

    override suspend fun <RowType : Any> getAsync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType =
        getOptionalAsync(sql, parameters, mapper) ?: throw PowerSyncException("get() called with query that returned no rows", null)

    private suspend inline fun <T> withStatement(
        sql: String,
        parameters: List<Any?>?,
        crossinline block: suspend (SQLiteStatement) -> T,
    ): T =
        rawConnection.usePreparedAsync(sql) { stmt ->
            stmt.bind(parameters)
            block(stmt)
        }
}

internal fun SQLiteStatement.bind(parameters: List<Any?>?) {
    parameters?.forEachIndexed { i, parameter ->
        // SQLite parameters are 1-indexed
        val index = i + 1

        when (parameter) {
            null -> {
                bindNull(index)
            }

            is Boolean -> {
                bindBoolean(index, parameter)
            }

            is String -> {
                bindText(index, parameter)
            }

            is Long -> {
                bindLong(index, parameter)
            }

            is Int -> {
                bindLong(index, parameter.toLong())
            }

            is Double -> {
                bindDouble(index, parameter)
            }

            is ByteArray -> {
                bindBlob(index, parameter)
            }

            else -> {
                if (parameter != null) {
                    throw IllegalArgumentException("Unsupported parameter type: ${parameter::class}, at index $index")
                }
            }
        }
    }
}
