package com.powersync.db.driver

import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.flow.SharedFlow

/**
 * An implementation of a connection pool providing asynchronous access to a single writer
 * and multiple readers.
 *
 * This is the underlying pool implementation on which the higher-level PowerSync Kotlin SDK is
 * built on. The SDK provides its own pool, but can also use existing implementations (via
 * [com.powersync.PowerSyncDatabase.opened]).
 */
public interface SQLiteConnectionPool {
    /**
     * Calls the callback with a read-only connection temporarily leased from the pool.
     */
    public suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T

    /**
     * Calls the callback with a read-write connection temporarily leased from the pool.
     */
    public suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T

    /**
     * Invokes the callback with all connections leased from the pool.
     */
    public suspend fun <R> withAllConnections(
        action: suspend (
            writer: SQLiteConnectionLease,
            readers: List<SQLiteConnectionLease>,
        ) -> R,
    )

    /**
     * Returns a flow of table updates made on the [write] connection.
     */
    public val updates: SharedFlow<Set<String>>

    /**
     * Closes the connection pool and associated resources.
     *
     * Calling [read], [write] and [withAllConnections] after calling [close] should result in an
     * exception.
     */
    public suspend fun close()
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect interface SQLiteConnectionLease {
    /**
     * Queries the autocommit state on the connection.
     */
    public suspend fun isInTransaction(): Boolean

    public suspend fun <R> usePreparedAsync(
        sql: String,
        block: suspend (SQLiteStatement) -> R,
    ): R

    public open suspend fun execSQL(sql: String)
}
