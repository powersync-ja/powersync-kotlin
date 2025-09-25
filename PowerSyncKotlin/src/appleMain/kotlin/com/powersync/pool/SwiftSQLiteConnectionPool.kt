package com.powersync.pool

import co.touchlab.kermit.Logger
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.db.runWrapped
import com.powersync.db.schema.Schema
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking

/**
 * Accepts a [SwiftPoolAdapter] to implement a [SQLiteConnectionPool] which
 * is usable by PowerSync.
 */
public open class SwiftSQLiteConnectionPool(
    private val adapter: SwiftPoolAdapter,
) : SQLiteConnectionPool {
    private val _updates = MutableSharedFlow<Set<String>>(replay = 0)
    override val updates: SharedFlow<Set<String>> get() = _updates

    init {
        adapter.linkExternalUpdates { tables ->
            _updates.emit(tables)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T {
        var result: T? = null
        adapter.leaseRead {
            runWrapped {
                /**
                 * For GRDB, this should be running inside the callback
                 * ```swift
                 * db.write {
                 *  // should be here
                 * }
                 * ```
                 */
                val lease =
                    RawConnectionLease(it)
                runBlocking {
                    result = callback(lease)
                }
            }
        }
        return result as T
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T {
        var result: T? = null
        var updates: Set<String> = emptySet()
        adapter.leaseWrite { lease ->
            runWrapped {
                val connectionLease = RawConnectionLease(lease)
                updates =
                    withSession(lease.pointer) {
                        runBlocking {
                            result = callback(connectionLease)
                        }
                    }
            }
        }
        // Inform the external adapter about the changes
        adapter.processPowerSyncUpdates(updates)
        // The adapter can pass these updates back to the shared flow
        return result as T
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun <R> withAllConnections(action: suspend (SQLiteConnectionLease, List<SQLiteConnectionLease>) -> R) {
        adapter.leaseAll { writerLease, readerLeases ->
            runWrapped {
                runBlocking {
                    action(
                        RawConnectionLease(writerLease),
                        readerLeases.map {
                            RawConnectionLease(it)
                        },
                    )
                }
            }
        }
    }

    override suspend fun close() {
        adapter.dispose()
    }
}

@OptIn(ExperimentalPowerSyncAPI::class, DelicateCoroutinesApi::class)
public fun openPowerSyncWithPool(
    pool: SQLiteConnectionPool,
    identifier: String,
    schema: Schema,
    logger: Logger,
): PowerSyncDatabase =
    PowerSyncDatabase.Companion.opened(
        pool = pool,
        scope = GlobalScope,
        schema = schema,
        identifier = identifier,
        logger = logger,
    )
