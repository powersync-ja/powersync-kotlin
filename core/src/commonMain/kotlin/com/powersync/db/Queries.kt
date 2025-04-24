package com.powersync.db

import com.powersync.PowerSyncException
import com.powersync.db.internal.ConnectionContext
import com.powersync.db.internal.PowerSyncTransaction
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

public fun interface ThrowableTransactionCallback<R> {
    @Throws(PowerSyncException::class, kotlinx.coroutines.CancellationException::class)
    public fun execute(transaction: PowerSyncTransaction): R
}

public fun interface ThrowableLockCallback<R> {
    @Throws(PowerSyncException::class, kotlinx.coroutines.CancellationException::class)
    public fun execute(context: ConnectionContext): R
}

public interface Queries {
    /**
     * Execute a write query (INSERT, UPDATE, DELETE)
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun execute(
        sql: String,
        parameters: List<Any?>? = listOf(),
    ): Long

    /**
     * Execute a read-only (SELECT) query and return a single result.
     * If there is no result, throws an [IllegalArgumentException].
     * See [getOptional] for queries where the result might be empty.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType

    /**
     * Execute a read-only (SELECT) query and return the results.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): List<RowType>

    /**
     * Execute a read-only (SELECT) query and return a single optional result.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType?

    /**
     * @returns a [Flow] which emits whenever the source tables are modified.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun onChange(
        tables: Set<String>,
        /**
         * Specify the minimum interval, in milliseconds, between queries.
         */
        throttleMs: Long? = null,
    ): Flow<Set<String>>

    /**
     * Execute a read-only (SELECT) query every time the source tables are modified and return the results as a List in [Flow].
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>? = listOf(),
        /**
         * Specify the minimum interval, in milliseconds, between queries.
         */
        throttleMs: Long? = null,
        mapper: (SqlCursor) -> RowType,
    ): Flow<List<RowType>>

    /**
     * Takes a global lock, without starting a transaction.
     *
     * This takes a global lock - only one write transaction can execute against
     * the database at a time. This applies even when constructing separate
     * database instances for the same database file.
     *
     * Locks for separate database instances on the same database file
     * may be held concurrently.
     *
     * In most cases, [writeTransaction] should be used instead.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <R> writeLock(callback: ThrowableLockCallback<R>): R

    /**
     * Open a read-write transaction.
     *
     * This takes a global lock - only one write transaction can execute against
     * the database at a time. This applies even when constructing separate
     * database instances for the same database file.
     *
     * Statements within the transaction must be done on the provided
     * [PowerSyncTransaction] - attempting statements on the database
     * instance will error cause a dead-lock.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <R> writeTransaction(callback: ThrowableTransactionCallback<R>): R

    /**
     * Takes a read lock, without starting a transaction.
     *
     * The lock only applies to a single SQLite connection, and multiple
     * connections may hold read locks at the same time.
     *
     * In most cases, [readTransaction] should be used instead.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <R> readLock(callback: ThrowableLockCallback<R>): R

    /**
     * Open a read-only transaction.
     *
     * Statements within the transaction must be done on the provided
     * [PowerSyncTransaction] - executing statements on the database level
     * will be executed on separate connections.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <R> readTransaction(callback: ThrowableTransactionCallback<R>): R
}
