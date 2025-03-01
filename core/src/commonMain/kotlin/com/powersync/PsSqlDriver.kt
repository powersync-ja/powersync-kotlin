package com.powersync

import app.cash.sqldelight.db.SqlDriver
import com.powersync.utils.AtomicMutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class PsSqlDriver(
    private val driver: SqlDriver,
    private val scope: CoroutineScope,
) : SqlDriver by driver {
    // MutableSharedFlow to emit batched table updates
    private val tableUpdatesFlow = MutableSharedFlow<Set<String>>(replay = 0)

    // In-memory buffer to store table names before flushing
    private val pendingUpdates =  AtomicMutableSet<String>()

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

    // Flows on table updates containing tables
    fun updatesOnTables(tableNames: Set<String>): Flow<Unit> = tableUpdatesFlow.asSharedFlow().filter { it.intersect(tableNames).isNotEmpty() }.map { }

    suspend fun fireTableUpdates() {
        val updates = pendingUpdates.toSet()
        pendingUpdates.clear()

        if (updates.isEmpty()) {
            return
        }

        tableUpdatesFlow.emit(updates)
    }
}
