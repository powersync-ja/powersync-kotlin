package com.powersync

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

public class PsSqlDriver(
    private val driver: SqlDriver,
    private val scope: CoroutineScope,
) : SqlDriver by driver {
    // MutableSharedFlow to emit batched table updates
    private val tableUpdatesFlow = MutableSharedFlow<List<String>>(replay = 0)

    // In-memory buffer to store table names before flushing
    private val pendingUpdates = mutableSetOf<String>()

    public fun updateTable(tableName: String) {
        pendingUpdates.add(tableName)
    }

    public fun clearTableUpdates() {
        pendingUpdates.clear()
    }

    // Flows on table updates
    public fun tableUpdates(): Flow<List<String>> = tableUpdatesFlow.asSharedFlow()

    // Flows on table updates containing a specific table
    public fun updatesOnTable(tableName: String): Flow<Unit> = tableUpdates().filter { it.contains(tableName) }.map { }

    public fun fireTableUpdates() {
        val updates = pendingUpdates.toList()
        if (updates.isEmpty()) {
            return
        }
        scope.launch {
            tableUpdatesFlow.emit(updates)
        }
        pendingUpdates.clear()
    }
}
