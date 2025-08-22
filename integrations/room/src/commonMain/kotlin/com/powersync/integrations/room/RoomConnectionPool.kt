package com.powersync.integrations.room

import androidx.room.RoomDatabase
import androidx.room.Transactor
import androidx.sqlite.SQLiteStatement
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

public class RoomConnectionPool(
    private val db: RoomDatabase,
    private val scope: CoroutineScope,
): SQLiteConnectionPool {
    private val _updates = MutableSharedFlow<Set<String>>()

    override suspend fun <R> withAllConnections(action: suspend (SQLiteConnectionLease, List<SQLiteConnectionLease>) -> R) {
        // We can't obtain a list of all connections on Room. That's fine though, we expect this to
        // be used with raw tables, and withAllConnections is only used to apply a PowerSync schema.
        action(write(), emptyList())
    }

    override suspend fun read(): SQLiteConnectionLease {
        return obtainLease(true)
    }

    override suspend fun write(): SQLiteConnectionLease {
        return obtainLease(false)
    }

    private suspend fun obtainLease(readonly: Boolean): SQLiteConnectionLease {
        val obtainedLease = CompletableDeferred<SQLiteConnectionLease>()
        scope.launch {
            db.useConnection(readonly) { transactor ->
                val completer = CompletableDeferred<Unit>()
                obtainedLease.complete(RoomTransactionLease(transactor, completer))
                completer.await()
            }
        }

        val lease = obtainedLease.await()
        return lease
    }

    override val updates: SharedFlow<Set<String>>
        get() = _updates

    override suspend fun close() {
        // Noop, Room database managed independently
    }
}

private class RoomTransactionLease(
    private val transactor: Transactor,
    private val completer: CompletableDeferred<Unit>
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

    override suspend fun close() {
        completer.complete(Unit)
    }
}
