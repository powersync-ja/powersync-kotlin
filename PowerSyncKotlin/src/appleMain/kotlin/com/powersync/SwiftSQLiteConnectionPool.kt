package com.powersync

import androidx.sqlite.SQLiteStatement
import cnames.structs.sqlite3
import co.touchlab.kermit.Logger
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.db.runWrapped
import com.powersync.db.schema.Schema
import com.powersync.sqlite.Database
import io.ktor.utils.io.CancellationException
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalPowerSyncAPI::class)
internal class RawConnectionLease
    @OptIn(ExperimentalForeignApi::class)
    constructor(
        private val lease: SwiftLeaseAdapter,
    ) : SQLiteConnectionLease {
        private var isCompleted = false

        @OptIn(ExperimentalForeignApi::class)
        private var db = Database(lease.pointer)

        private fun checkNotCompleted() {
            check(!isCompleted) { "Connection lease already closed" }
        }

        override suspend fun isInTransaction(): Boolean = isInTransactionSync()

        override fun isInTransactionSync(): Boolean {
            checkNotCompleted()
            return db.inTransaction()
        }

        override suspend fun <R> usePrepared(
            sql: String,
            block: (SQLiteStatement) -> R,
        ): R = usePreparedSync(sql, block)

        override fun <R> usePreparedSync(
            sql: String,
            block: (SQLiteStatement) -> R,
        ): R {
            checkNotCompleted()
            return db.prepare(sql).use(block)
        }
    }

public fun interface LeaseCallback {
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun execute(lease: SwiftLeaseAdapter)
}

public fun interface AllLeaseCallback {
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun execute(
        writeLease: SwiftLeaseAdapter,
        readLeases: List<SwiftLeaseAdapter>,
    )
}

public interface SwiftLeaseAdapter {
    @OptIn(ExperimentalForeignApi::class)
    public val pointer: CPointer<sqlite3>
}

/**
 *  We only allow synchronous callbacks on the Swift side for leased READ/WRITE connections.
 *  We also get a SQLite connection pointer (sqlite3*) from Swift side. which is used in a [Database]
 */

public interface SwiftPoolAdapter {
    @OptIn(ExperimentalForeignApi::class)
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun leaseRead(callback: LeaseCallback)

    @OptIn(ExperimentalForeignApi::class)
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun leaseWrite(callback: LeaseCallback)

    @OptIn(ExperimentalForeignApi::class)
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun leaseAll(callback: AllLeaseCallback)

    public fun linkUpdates(callback: suspend (Set<String>) -> Unit)

    public suspend fun closePool()
}

@OptIn(ExperimentalPowerSyncAPI::class)
public open class SwiftSQLiteConnectionPool
    @OptIn(ExperimentalForeignApi::class)
    constructor(
        private val adapter: SwiftPoolAdapter,
    ) : SQLiteConnectionPool {
        private val _updates = MutableSharedFlow<Set<String>>(replay = 0)
        override val updates: SharedFlow<Set<String>> get() = _updates

        init {
            adapter.linkUpdates { tables ->
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
            adapter.leaseWrite { lease ->
                runWrapped {
                    val lease = RawConnectionLease(lease)

                    runBlocking {
                        result = callback(lease)
                    }
                }
            }
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
            adapter.closePool()
        }
    }

@OptIn(ExperimentalPowerSyncAPI::class, DelicateCoroutinesApi::class)
public fun openPowerSyncWithPool(
    pool: SQLiteConnectionPool,
    identifier: String,
    schema: Schema,
    logger: Logger,
): PowerSyncDatabase =
    PowerSyncDatabase.opened(
        pool = pool,
        scope = GlobalScope,
        schema = schema,
        identifier = identifier,
        logger = logger,
    )
