package com.powersync.db

import com.powersync.db.internal.PowerSyncTransaction
import kotlinx.coroutines.flow.Flow

public interface Queries {
    /**
     * Execute a write query (INSERT, UPDATE, DELETE)
     */
    public suspend fun execute(
        sql: String,
        parameters: List<Any?>? = listOf(),
    ): Long

    /**
     * Execute a read-only (SELECT) query and return a single result.
     * If there is no result, throws an [IllegalArgumentException].
     * See [getOptional] for queries where the result might be empty.
     */
    public suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType

    /**
     * Execute a read-only (SELECT) query and return the results.
     */
    public suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): List<RowType>

    /**
     * Execute a read-only (SELECT) query and return a single optional result.
     */
    public suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType?

    /**
     * Execute a read-only (SELECT) query every time the source tables are modified and return the results as a List in [Flow].
     */
    public fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): Flow<List<RowType>>

    public suspend fun <R> writeTransaction(callback: (PowerSyncTransaction) -> R): R

    public suspend fun <R> readTransaction(callback: (PowerSyncTransaction) -> R): R
}
