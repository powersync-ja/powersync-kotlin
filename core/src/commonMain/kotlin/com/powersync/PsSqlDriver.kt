package com.powersync

import app.cash.sqldelight.db.SqlDriver
import com.powersync.utils.AtomicMutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal class PsSqlDriver(
    private val driver: SqlDriver,
    private val scope: CoroutineScope,
) : SqlDriver by driver {
    // MutableSharedFlow to emit batched table updates
    private val tableUpdatesFlow = MutableSharedFlow<Set<String>>(replay = 0)

    // In-memory buffer to store table names before flushing
    private val pendingUpdates = AtomicMutableSet<String>()

    fun updateTable(tableName: String) {
        scope.launch {
            pendingUpdates.add(tableName)
        }
    }

    fun clearTableUpdates() {
        scope.launch {
            pendingUpdates.clear()
        }
    }

    // Flows on any table change
    // This specifically returns a SharedFlow for downstream timing considerations
    fun updatesOnTables(): SharedFlow<Set<String>> =
        tableUpdatesFlow
            .asSharedFlow()

    suspend fun fireTableUpdates() {
        // Use the same scope as the async table updates, this should help with queuing
        val job =
            scope.async {
                val updates = pendingUpdates.toSet(true)
                tableUpdatesFlow.emit(updates)
            }
        job.await()
    }
}
