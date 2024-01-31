package com.powersync.db

import app.cash.sqldelight.SuspendingTransactionWithReturn
import app.cash.sqldelight.db.SqlCursor
import kotlinx.coroutines.flow.Flow

interface ReadQueries {

    /**
     * Execute a read-only (SELECT) query and return a single result.
     */
    suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): RowType

    /**
     * Execute a read-only (SELECT) query and return the results.
     */
    suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): List<RowType>

    /**
     * Execute a read-only (SELECT) query and return a single optional result.
     */
    suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): RowType?

    /**
     * Execute a read-only (SELECT) query every time the source tables are modified and return the results as a List in [Flow].
     */
    suspend fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): Flow<List<RowType>>


    suspend fun <R> readTransaction(body: suspend SuspendingTransactionWithReturn<R>.() -> R): R

}


