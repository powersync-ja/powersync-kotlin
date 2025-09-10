package com.powersync.integrations.room

import androidx.room.RoomDatabase
import androidx.room.Transactor
import androidx.room.execSQL
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/**
 * A [SQLiteConnectionPool] implementation for the PowerSync SDK that is backed by a [RoomDatabase].
 *
 * An instance of this class can be passed to [com.powersync.PowerSyncDatabase.opened], allowing
 * PowerSync to wrap Room databases.
 *
 * Writes made from the wrapped PowerSync database, including writes made for the sync process, are
 * forwarded to Room and will update your flows automatically.
 * On the other hand, the PowerSync SDK needs to be notified about updates in Room. For that, use
 * the [transferRoomUpdatesToPowerSync] method as a collector of a Room flow listening on all your
 * tables.
 */
public class RoomConnectionPool(
    private val db: RoomDatabase,
) : SQLiteConnectionPool {
    private val _updates = MutableSharedFlow<Set<String>>()
    private var hasInstalledUpdateHook = false

    override suspend fun <R> withAllConnections(action: suspend (SQLiteConnectionLease, List<SQLiteConnectionLease>) -> R) {
        // We can't obtain a list of all connections on Room. That's fine though, we expect this to
        // be used with raw tables, and withAllConnections is only used to apply a PowerSync schema.
        write {
            action(it, emptyList())
        }
    }

    override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T =
        db.useReaderConnection {
            callback(RoomTransactionLease(it, currentCoroutineContext()))
        }

    /**
     * Makes pending updates tracked by Room's invalidation tracker available to the PowerSync
     * database, updating flows and triggering CRUD uploads.
     */
    public suspend fun transferRoomUpdatesToPowerSync() {
        write {
            // The end of the write callback invokes powersync_update_hooks('get') for this
        }
    }

    override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T =
        db.useWriterConnection {
            if (!hasInstalledUpdateHook) {
                hasInstalledUpdateHook = true
                it.execSQL("SELECT powersync_update_hooks('install')")
            }

            try {
                callback(RoomTransactionLease(it, currentCoroutineContext()))
            } finally {
                val changed =
                    it.usePrepared("SELECT powersync_update_hooks('get')") { stmt ->
                        check(stmt.step())
                        json.decodeFromString<Set<String>>(stmt.getText(0))
                    }

                val userTables =
                    changed
                        .filter { tbl ->
                            !tbl.startsWith("ps_") && !tbl.startsWith("room_")
                        }.toTypedArray()

                if (userTables.isNotEmpty()) {
                    db.invalidationTracker.refresh(*userTables)
                }

                _updates.emit(changed)
            }
        }

    override val updates: SharedFlow<Set<String>>
        get() = _updates

    override suspend fun close() {
        // Noop, Room database managed independently
    }

    private companion object {
        val json = Json {}
    }
}

private class RoomTransactionLease(
    private val transactor: Transactor,
    /**
     * The context to use for [runBlocking] calls to avoid the "Attempted to use connection on a
     * different coroutine" error.
     */
    private val context: CoroutineContext,
) : SQLiteConnectionLease {
    override suspend fun isInTransaction(): Boolean = transactor.inTransaction()

    override suspend fun <R> usePrepared(
        sql: String,
        block: (SQLiteStatement) -> R,
    ): R = transactor.usePrepared(sql, block)

    override fun isInTransactionSync(): Boolean =
        runBlocking(context) {
            isInTransaction()
        }

    override fun <R> usePreparedSync(
        sql: String,
        block: (SQLiteStatement) -> R,
    ): R =
        runBlocking(context) {
            usePrepared(sql, block)
        }
}
