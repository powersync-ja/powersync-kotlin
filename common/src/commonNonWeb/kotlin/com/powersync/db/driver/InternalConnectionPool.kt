package com.powersync.db.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PersistentConnectionFactory
import com.powersync.utils.JsonUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalPowerSyncAPI::class)
internal class InternalConnectionPool(
    private val factory: PersistentConnectionFactory,
    scope: CoroutineScope,
    private val dbFilename: String,
    private val dbDirectory: String?,
    private val writeLockMutex: Mutex,
    /**
     * Database calls are synchronous/blocking, so we always run them on Dispatchers.IO instead of
     * inheriting the caller-provided scope context. The provided scope is still used for
     * lifecycle-bound pool coroutines like read workers and update emission.
     */
    private val dispatcher: CoroutineContext,
) : SQLiteConnectionPool {
    private val writeConnection = newConnection(false)
    private val readPool = ReadPool({ newConnection(true) }, scope = scope)

    private fun newConnection(readOnly: Boolean): SQLiteConnection {
        val connection =
            factory.openConnection(
                dbFilename = dbFilename,
                dbDirectory = dbDirectory,
                readOnly = false,
            )

        connection.setupDefaultPragmas(readOnly)
        return connection
    }

    override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T =
        readPool.read { connection ->
            withContext(dispatcher) {
                callback(connection)
            }
        }

    override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T =
        writeLockMutex.withLock {
            try {
                withContext(dispatcher) {
                    callback(RawConnectionLease(writeConnection))
                }
            } finally {
                // When we've leased a write connection, we may have to update table update flows
                // after users ran their custom statements. Reading updates is a SQL call, but it
                // doesn't do IO so we can do it on the main dispatcher.
                val updatedTables = writeConnection.readPendingUpdates()
                if (updatedTables.isNotEmpty()) {
                    updates.emit(updatedTables)
                }
            }
        }

    override suspend fun <R> withAllConnections(action: suspend (SQLiteConnectionLease, List<SQLiteConnectionLease>) -> R) {
        // First get a lock on all read connections
        readPool.withAllConnections { rawReadConnections ->
            val readers = rawReadConnections.map { RawConnectionLease(it) }
            // Then get access to the write connection
            // The write call will dispatch to the provided dispatcher
            write { writer ->
                action(writer, readers)
            }
        }
    }

    // MutableSharedFlow to emit batched table updates
    override val updates: SharedFlow<Set<String>>
        field = MutableSharedFlow<Set<String>>(replay = 0)

    override suspend fun close() {
        writeConnection.close()
        readPool.close()
    }
}

internal fun SQLiteConnection.setupDefaultPragmas(readOnly: Boolean) {
    if (readOnly) {
        execSQL("pragma query_only = TRUE")
    } else {
        execSQL("pragma journal_mode = WAL")
    }

    execSQL("pragma journal_size_limit = ${6 * 1024 * 1024}")
    execSQL("pragma busy_timeout = 30000")
    execSQL("pragma cache_size = -${50 * 1024}")

    // Older versions of the SDK used to set up an empty schema and raise the user version to 1.
    // Keep doing that for consistency.
    if (!readOnly) {
        val version =
            prepare("pragma user_version").use {
                require(it.step())
                if (it.isNull(0)) 0L else it.getLong(0)
            }
        if (version < 1L) {
            execSQL("pragma user_version = 1")
        }

        // Also install a commit, rollback and update hooks in the core extension to implement
        // the updates flow here (not all our driver implementations support hooks, so this is
        // a more reliable fallback).
        execSQL("select powersync_update_hooks('install');")
    }
}

internal fun SQLiteConnection.readPendingUpdates(): Set<String> =
    prepare("SELECT powersync_update_hooks('get')").use {
        check(it.step())
        val updatedTables = JsonUtil.json.decodeFromString<Set<String>>(it.getText(0))
        updatedTables
    }
