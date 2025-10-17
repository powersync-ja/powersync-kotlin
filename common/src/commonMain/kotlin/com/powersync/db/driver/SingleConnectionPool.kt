package com.powersync.db.driver

import androidx.sqlite.SQLiteConnection
import com.powersync.ExperimentalPowerSyncAPI
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [SQLiteConnectionPool] backed by a single database connection.
 *
 * This does not provide any concurrency, but is still a reasonable implementation to use for e.g. tests.
 */
@OptIn(ExperimentalPowerSyncAPI::class)
public class SingleConnectionPool(
    private val conn: SQLiteConnection,
) : SQLiteConnectionPool {
    private val mutex: Mutex = Mutex()
    private var closed = false
    private val tableUpdatesFlow = MutableSharedFlow<Set<String>>(replay = 0)

    init {
        conn.setupDefaultPragmas(false)
    }

    override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T = write(callback)

    override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T =
        mutex.withLock {
            check(!closed) { "Connection closed" }

            try {
                callback(RawConnectionLease(conn))
            } finally {
                val updates = conn.readPendingUpdates()
                if (updates.isNotEmpty()) {
                    tableUpdatesFlow.emit(updates)
                }
            }
        }

    override suspend fun <R> withAllConnections(
        action: suspend (writer: SQLiteConnectionLease, readers: List<SQLiteConnectionLease>) -> R,
    ): Unit = write { writer ->
        action(writer, emptyList())
    }

    override val updates: SharedFlow<Set<String>>
        get() = tableUpdatesFlow

    override suspend fun close() {
        mutex.withLock {
            conn.close()
        }
    }
}
