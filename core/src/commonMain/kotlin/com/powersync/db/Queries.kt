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
     * Executes a write query (INSERT, UPDATE, DELETE).
     *
     * @param sql The SQL query to execute.
     * @param parameters The parameters for the query, or an empty list if none.
     * @return The number of rows affected by the query.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun execute(
        sql: String,
        parameters: List<Any?>? = listOf(),
    ): Long

    /**
     * Executes a read-only (SELECT) query and returns a single result.
     *
     * @param sql The SQL query to execute.
     * @param parameters The parameters for the query, or an empty list if none.
     * @param mapper A function to map the result set to the desired type.
     * @return The single result of the query.
     * @throws PowerSyncException If a database error occurs or no result is found.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType

    /**
     * Executes a read-only (SELECT) query and returns all results.
     *
     * @param sql The SQL query to execute.
     * @param parameters The parameters for the query, or an empty list if none.
     * @param mapper A function to map the result set to the desired type.
     * @return A list of results.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): List<RowType>

    /**
     * Executes a read-only (SELECT) query and returns a single optional result.
     *
     * @param sql The SQL query to execute.
     * @param parameters The parameters for the query, or an empty list if none.
     * @param mapper A function to map the result set to the desired type.
     * @return The single result of the query, or null if no result is found.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType?

    /**
     * Returns a [Flow] that emits whenever the source tables are modified.
     *
     * @param tables The set of tables to monitor for changes.
     * @param throttleMs The minimum interval, in milliseconds, between queries. Defaults to null.
     * @return A [Flow] emitting the set of modified tables.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun onChange(
        tables: Set<String>,
        throttleMs: Long? = null,
    ): Flow<Set<String>>

    /**
     * Executes a read-only (SELECT) query every time the source tables are modified and returns the results as a [Flow] of lists.
     *
     * @param sql The SQL query to execute.
     * @param parameters The parameters for the query, or an empty list if none.
     * @param throttleMs The minimum interval, in milliseconds, between queries. Defaults to null.
     * @param mapper A function to map the result set to the desired type.
     * @return A [Flow] emitting lists of results.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>? = listOf(),
        throttleMs: Long? = null,
        mapper: (SqlCursor) -> RowType,
    ): Flow<List<RowType>>

    /**
     * Takes a global lock without starting a transaction.
     *
     * This lock ensures that only one write transaction can execute against the database at a time, even across separate database instances for the same file.
     *
     * In most cases, [writeTransaction] should be used instead.
     *
     * @param callback The callback to execute while holding the lock.
     * @return The result of the callback.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <R> writeLock(callback: ThrowableLockCallback<R>): R

    /**
     * Opens a read-write transaction.
     *
     * This takes a global lock, ensuring that only one write transaction can execute against the database at a time, even across separate database instances for the same file.
     *
     * Statements within the transaction must be done on the provided [PowerSyncTransaction] - attempting statements on the database instance will error cause a dead-lock.
     *
     * @param callback The callback to execute within the transaction.
     * @return The result of the callback.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <R> writeTransaction(callback: ThrowableTransactionCallback<R>): R

    /**
     * Takes a read lock without starting a transaction.
     *
     * The lock applies only to a single SQLite connection, allowing multiple connections to hold read locks simultaneously.
     *
     * @param callback The callback to execute while holding the lock.
     * @return The result of the callback.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <R> readLock(callback: ThrowableLockCallback<R>): R

    /**
     * Opens a read-only transaction.
     *
     * Statements within the transaction must be done on the provided [PowerSyncTransaction] - executing statements on the database level will be executed on separate connections.
     *
     * @param callback The callback to execute within the transaction.
     * @return The result of the callback.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun <R> readTransaction(callback: ThrowableTransactionCallback<R>): R
}
