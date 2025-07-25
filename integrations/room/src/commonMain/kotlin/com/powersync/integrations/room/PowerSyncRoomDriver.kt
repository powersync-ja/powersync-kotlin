package com.powersync.integrations.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import com.powersync.PowerSyncDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

public class PowerSyncRoomDriver(
    private val db: PowerSyncDatabase,
    private val scope: CoroutineScope,
): SQLiteDriver {
    override val hasConnectionPool: Boolean
        // The PowerSync database has a connection pool internally, so Room shouldn't roll its own.
        get() = true

    override fun open(fileName: String): SQLiteConnection {
        return PowerSyncConnection(db, scope)
    }
}

private class PowerSyncConnection(
    private val db: PowerSyncDatabase,
    private val scope: CoroutineScope,
): SQLiteConnection {
    // We lazily request an underlying SQLite connection when necessary, and release it as quickly
    // as possible so that other concurrent PowerSync operations can run.
    private var currentConnection: LeasedConnection? = null

    private fun obtainConnection(): SQLiteConnection {
        currentConnection?.let {
            return it.inner
        }

        val completeConnection = CompletableDeferred<SQLiteConnection>()

        scope.launch {
            db.writeLock { inner ->
                val connectionReturned = CompletableDeferred<Unit>()
                val lease = LeasedConnection(inner.rawConnection, connectionReturned)
                completeConnection.complete(lease.inner)

                connectionReturned.await()
            }
        }

        return runBlocking { completeConnection.await() }
    }

    private fun returnConnection() {
        currentConnection?.returnConnection()
        currentConnection = null
    }

    override fun prepare(sql: String): SQLiteStatement {
        val completeConnection = CompletableDeferred<SQLiteConnection>()

        scope.launch {
            db.writeLock { inner ->
                val connection = inner.rawConnection

            }
        }

        TODO("Not yet implemented")
    }

    override fun close() {
        returnConnection()
    }
}

private class LeasedConnection(
    val inner: SQLiteConnection,
    val complete: CompletableDeferred<Unit>
) {
    fun returnConnection() {
        complete.complete(Unit)
    }
}