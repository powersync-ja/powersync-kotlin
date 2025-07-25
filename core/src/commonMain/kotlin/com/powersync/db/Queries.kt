package com.powersync.db

import androidx.sqlite.SQLiteConnection
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import com.powersync.db.internal.ConnectionContext
import com.powersync.db.internal.PowerSyncTransaction
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

public fun interface ThrowableTransactionCallback<R> {
    @Throws(PowerSyncException::class, kotlinx.coroutines.CancellationException::class)
    public fun execute(transaction: PowerSyncTransaction): R
}

public fun interface ThrowableLockCallback<R> {
    @Throws(PowerSyncException::class, kotlinx.coroutines.CancellationException::class)
    public fun execute(context: ConnectionContext): R
}

public interface Queries {
    public companion object {
        /**
         * The default throttle duration for  [onChange] and [watch] operations.
         */
        public val DEFAULT_THROTTLE: Duration = 30.milliseconds
    }

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
     * @param throttleMs The minimum interval, in milliseconds, between emissions. Defaults to [DEFAULT_THROTTLE]. Table changes are accumulated while throttling is active. The accumulated set of tables will be emitted on the trailing edge of the throttle.
     * @param triggerImmediately If true (default), the flow will immediately emit an empty set of tables when the flow is first collected. This can be useful for ensuring that the flow emits at least once, even if no changes occur to the monitored tables.
     * @return A [Flow] emitting the set of modified tables.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun onChange(
        tables: Set<String>,
        throttleMs: Long = DEFAULT_THROTTLE.inWholeMilliseconds,
        triggerImmediately: Boolean = true,
    ): Flow<Set<String>>

    /**
     * Executes a read-only (SELECT) query every time the source tables are modified and returns the results as a [Flow] of lists.
     *
     * @param sql The SQL query to execute.
     * @param parameters The parameters for the query, or an empty list if none.
     * @param throttleMs The minimum interval, in milliseconds, between queries. Defaults to [DEFAULT_THROTTLE].
     * @param mapper A function to map the result set to the desired type.
     * @return A [Flow] emitting lists of results.
     * @throws PowerSyncException If a database error occurs.
     * @throws CancellationException If the operation is cancelled.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>? = listOf(),
        throttleMs: Long = DEFAULT_THROTTLE.inWholeMilliseconds,
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

    /**
     * Obtains a connection from the read pool or an exclusive reference on the write connection.
     *
     * This is useful when you need full control over the raw statements to use.
     *
     * The connection needs to be released by calling [SQLiteConnection.close] as soon as you're
     * done with it, because the connection will occupy a read resource or the write lock while
     * active.
     *
     * Misusing this API, for instance by not cleaning up transactions started on the underlying
     * connection with a `BEGIN` statement or forgetting to close it, can disrupt the rest of the
     * PowerSync SDK. For this reason, this method should only be used if absolutely necessary.
     */
    @ExperimentalPowerSyncAPI()
    @HiddenFromObjC()
    public suspend fun leaseConnection(readOnly: Boolean = false): SQLiteConnection
}
