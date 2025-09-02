package com.powersync.db.driver

import androidx.sqlite.SQLiteStatement
import com.powersync.ExperimentalPowerSyncAPI
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking

/**
 * An implementation of a connection pool providing asynchronous access to a single writer
 * and multiple readers.
 *
 * This is the underlying pool implementation on which the higher-level PowerSync Kotlin SDK is
 * built on. The SDK provides its own pool, but can also use existing implementations (via
 * [com.powersync.PowerSyncDatabase.opened]).
 */
@ExperimentalPowerSyncAPI()
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
    public suspend fun <R> withAllConnections(action: suspend (
        writer: SQLiteConnectionLease,
        readers: List<SQLiteConnectionLease>
    ) -> R)

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

@ExperimentalPowerSyncAPI
public interface SQLiteConnectionLease {
    /**
     * Queries the autocommit state on the connection.
     */
    public suspend fun isInTransaction(): Boolean

    public fun isInTransactionSync(): Boolean {
        return runBlocking { isInTransaction() }
    }

    /**
     * Prepares [sql] as statement and runs [block] with it.
     *
     * Block most only run on a single-thread. The statement must not be used once [block] returns.
     */
    public suspend fun <R> usePrepared(sql: String, block: (SQLiteStatement) -> R): R

    public fun <R> usePreparedSync(sql: String, block: (SQLiteStatement) -> R): R {
        return runBlocking {
            usePrepared(sql, block)
        }
    }

    public suspend fun execSQL(sql: String) {
        usePrepared(sql) {
            it.step()
        }
    }
}
