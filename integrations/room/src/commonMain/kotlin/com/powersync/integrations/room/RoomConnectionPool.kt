package com.powersync.integrations.room

import androidx.room.RoomDatabase
import androidx.room.Transactor
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

public class RoomConnectionPool(
    private val db: RoomDatabase,
): SQLiteConnectionPool {
    private val _updates = MutableSharedFlow<Set<String>>()

    override suspend fun <R> withAllConnections(action: suspend (SQLiteConnectionLease, List<SQLiteConnectionLease>) -> R) {
        // We can't obtain a list of all connections on Room. That's fine though, we expect this to
        // be used with raw tables, and withAllConnections is only used to apply a PowerSync schema.
        write {
            action(it, emptyList())
        }
    }

    override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T {
        return db.useReaderConnection {
            callback(RoomTransactionLease(it))
        }
    }

    override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T {
        return db.useWriterConnection {
            callback(RoomTransactionLease(it))
        }
    }

    override val updates: SharedFlow<Set<String>>
        get() = _updates

    override suspend fun close() {
        // Noop, Room database managed independently
    }
}

private class RoomTransactionLease(
    private val transactor: Transactor,
): SQLiteConnectionLease {
    override suspend fun isInTransaction(): Boolean {
        return transactor.inTransaction()
    }

    override suspend fun <R> usePrepared(
        sql: String,
        block: (SQLiteStatement) -> R
    ): R {
        return transactor.usePrepared(sql, block)
    }
}
