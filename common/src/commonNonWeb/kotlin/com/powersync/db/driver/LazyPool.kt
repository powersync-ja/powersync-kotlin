package com.powersync.db.driver

import com.powersync.ExperimentalPowerSyncAPI
import kotlinx.coroutines.flow.SharedFlow

/**
 * A [SQLiteConnectionPool] implemented by constructing an inner pool on first access.
 *
 * This allows [InternalConnectionPool] to construct connections immediately (which potentially
 * throws an exception that we want to report when the SDK is actually used instead of when it's
 * first constructed).
 */
@OptIn(ExperimentalPowerSyncAPI::class)
internal class LazyPool(
    openInner: () -> SQLiteConnectionPool,
) : SQLiteConnectionPool {
    private val lazyPool = lazy(openInner)
    private val pool by lazyPool

    override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T = pool.read(callback)

    override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T = pool.write(callback)

    override suspend fun <R> withAllConnections(action: suspend (SQLiteConnectionLease, List<SQLiteConnectionLease>) -> R) =
        pool.withAllConnections(action)

    override val updates: SharedFlow<Set<String>>
        get() = pool.updates

    override suspend fun close() {
        if (lazyPool.isInitialized()) {
            pool.close()
        }
    }
}
