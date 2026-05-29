package com.powersync.db.internal

import androidx.sqlite.SQLiteStatement
import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor
import com.powersync.db.StatementBasedCursor
import com.powersync.db.driver.SQLiteConnectionLease

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual interface ConnectionContext {
    public actual suspend fun executeAsync(
        sql: String,
        parameters: List<Any?>?,
    ): Long

    public actual suspend fun <RowType : Any> getOptionalAsync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType?

    public actual suspend fun <RowType : Any> getAllAsync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType>

    public actual suspend fun <RowType : Any> getAsync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType

    @Throws(PowerSyncException::class)
    public fun execute(
        sql: String,
        parameters: List<Any?>? = listOf(),
    ): Long

    @Throws(PowerSyncException::class)
    public fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType?

    @Throws(PowerSyncException::class)
    public fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): List<RowType>

    @Throws(PowerSyncException::class)
    public fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType
}

internal actual fun SQLiteConnectionLease.asContext(): ConnectionContext = LeaseContext(this)

private class LeaseContext(
    rawConnection: SQLiteConnectionLease,
) : BaseConnectionContextImplementation(rawConnection) {
    override fun execute(
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

    override fun <RowType : Any> getOptional(
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

    override fun <RowType : Any> getAll(
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

    override fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType =
        getOptional(sql, parameters, mapper) ?: throw PowerSyncException(
            "get() called with query that returned no rows",
            null,
        )

    private inline fun <T> withStatement(
        sql: String,
        parameters: List<Any?>?,
        crossinline block: (SQLiteStatement) -> T,
    ): T =
        rawConnection.usePreparedSync(sql) { stmt ->
            stmt.bind(parameters)
            block(stmt)
        }
}
