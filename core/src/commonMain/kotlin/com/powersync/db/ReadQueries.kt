package com.powersync.db

import app.cash.sqldelight.SuspendingTransactionWithReturn
import app.cash.sqldelight.db.SqlCursor
import kotlinx.coroutines.flow.Flow

public interface ReadQueries {

    /**
     * Execute a read-only (SELECT) query and return a single result.
     */
    public suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): RowType

    /**
     * Execute a read-only (SELECT) query and return the results.
     */
    public suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): List<RowType>

    /**
     * Execute a read-only (SELECT) query and return a single optional result.
     */
    public suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): RowType?

    /**
     * Execute a read-only (SELECT) query every time the source tables are modified and return the results as a List in [Flow].
     */
    public fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): Flow<List<RowType>>


    public suspend fun <R> readTransaction(body: suspend SuspendingTransactionWithReturn<R>.() -> R): R

}


