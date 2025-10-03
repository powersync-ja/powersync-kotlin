package com.powersync.db.driver

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.powersync.DatabaseDriverFactory
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.openDatabase
import com.powersync.utils.JsonUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalPowerSyncAPI::class)
internal class InternalConnectionPool(
    private val factory: DatabaseDriverFactory,
    private val scope: CoroutineScope,
    private val dbFilename: String,
    private val dbDirectory: String?,
    private val writeLockMutex: Mutex,
) : SQLiteConnectionPool {
    private val writeConnection = newConnection(false)
    private val readPool = ReadPool({ newConnection(true) }, scope = scope)

    // MutableSharedFlow to emit batched table updates
    private val tableUpdatesFlow = MutableSharedFlow<Set<String>>(replay = 0)

    private fun newConnection(readOnly: Boolean): SQLiteConnection {
        val connection =
            openDatabase(
                factory = factory,
                dbFilename = dbFilename,
                dbDirectory = dbDirectory,
                readOnly = false,
            )

        connection.setupDefaultPragmas(readOnly)
        return connection
    }

    override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T = readPool.read(callback)

    override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T =
        writeLockMutex.withLock {
            try {
                callback(RawConnectionLease(writeConnection))
            } finally {
                // When we've leased a write connection, we may have to update table update flows
                // after users ran their custom statements.
                val updatedTables = writeConnection.readPendingUpdates()
                if (updatedTables.isNotEmpty()) {
                    scope.launch {
                        tableUpdatesFlow.emit(updatedTables)
                    }
                }
            }
        }

    override suspend fun <R> withAllConnections(action: suspend (SQLiteConnectionLease, List<SQLiteConnectionLease>) -> R) {
        // First get a lock on all read connections
        readPool.withAllConnections { rawReadConnections ->
            val readers = rawReadConnections.map { RawConnectionLease(it) }
            // Then get access to the write connection
            write { writer ->
                action(writer, readers)
            }
        }
    }

    override val updates: SharedFlow<Set<String>>
        get() = tableUpdatesFlow

    override suspend fun close() {
        writeConnection.close()
        readPool.close()
    }
}

internal fun SQLiteConnection.setupDefaultPragmas(readOnly: Boolean) {
    execSQL("pragma journal_mode = WAL")
    execSQL("pragma journal_size_limit = ${6 * 1024 * 1024}")
    execSQL("pragma busy_timeout = 30000")
    execSQL("pragma cache_size = ${50 * 1024}")

    if (readOnly) {
        execSQL("pragma query_only = TRUE")
    }

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
