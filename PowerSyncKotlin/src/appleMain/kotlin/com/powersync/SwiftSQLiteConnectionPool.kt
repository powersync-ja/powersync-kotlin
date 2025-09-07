package com.powersync

import androidx.sqlite.SQLiteStatement
import cnames.structs.sqlite3
import co.touchlab.kermit.Logger
import com.powersync.db.ActiveDatabaseGroup
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
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
        connectionPointer: CPointer<sqlite3>,
    ) : SQLiteConnectionLease {
        private var isCompleted = false

        @OptIn(ExperimentalForeignApi::class)
        private var db = Database(connectionPointer)

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

/**
 *  We only allow synchronous callbacks on the Swift side for leased READ/WRITE connections.
 *  We also get a SQLite connection pointer (sqlite3*) from Swift side. which is used in a [Database]
 */

public interface SwiftPoolAdapter {
    @OptIn(ExperimentalForeignApi::class)
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun leaseRead(callback: (CPointer<sqlite3>) -> Unit)

    @OptIn(ExperimentalForeignApi::class)
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun leaseWrite(callback: (CPointer<sqlite3>) -> Unit)

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

        public fun pushUpdate(update: Set<String>) {
            _updates.tryEmit(update)
        }

        @OptIn(ExperimentalForeignApi::class)
        override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T {
            var result: T? = null
            adapter.leaseRead {
                /**
                 * For GRDB, this should be running inside the callback
                 * ```swift
                 * db.write {
                 *  // should be here
                 * }
                 * ```
                 */
                val lease = RawConnectionLease(it)
                runBlocking {
                    result = callback(lease)
                }
            }
            return result as T
        }

        @OptIn(ExperimentalForeignApi::class)
        override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T {
            var result: T? = null
            adapter.leaseRead {
                val lease = RawConnectionLease(it)
                runBlocking {
                    result = callback(lease)
                }
            }
            return result as T
        }

        override suspend fun <R> withAllConnections(action: suspend (SQLiteConnectionLease, List<SQLiteConnectionLease>) -> R) {
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
): PowerSyncDatabase {
    val activeDatabaseGroup = ActiveDatabaseGroup.referenceDatabase(logger, identifier)
    return PowerSyncDatabase.opened(
        pool = pool,
        scope = GlobalScope,
        schema = schema,
        group = activeDatabaseGroup,
        logger = logger,
    )
}
