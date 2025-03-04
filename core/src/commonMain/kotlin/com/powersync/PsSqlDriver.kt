package com.powersync

import app.cash.sqldelight.db.SqlDriver
import com.powersync.utils.AtomicMutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

internal class PsSqlDriver(
    private val driver: SqlDriver,
    private val scope: CoroutineScope,
) : SqlDriver by driver {
    // MutableSharedFlow to emit batched table updates
    private val tableUpdatesFlow = MutableSharedFlow<Set<String>>(replay = 0)

    // In-memory buffer to store table names before flushing
    private val pendingUpdates = AtomicMutableSet<String>()

    fun updateTable(tableName: String) {
        // This should only ever be executed by an execute operation which should
        // always be executed with the IO Dispatcher
        runBlocking {
            pendingUpdates.add(tableName)
        }
    }

    fun clearTableUpdates() {
        // This should only ever be executed on rollback which should be executed via the
        // IO Dispatcher.
        runBlocking {
            pendingUpdates.clear()
        }
    }

    // Flows on any table change
    // This specifically returns a SharedFlow for timing considerations
    fun updatesOnTables(): SharedFlow<Set<String>> {
        // Spread the input table names in order to account for internal views
        return tableUpdatesFlow
            .asSharedFlow()
    }

    suspend fun fireTableUpdates() {
        val updates = pendingUpdates.toSet(true)
        tableUpdatesFlow.emit(updates)
    }
}
