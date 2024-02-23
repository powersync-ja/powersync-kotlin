package com.powersync

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class PsSqliteDriver(private val driver: SqlDriver, private val scope: CoroutineScope) :
    SqlDriver by driver {
    // MutableSharedFlow to emit batched table updates
    private val tableUpdatesFlow = MutableSharedFlow<List<String>>(replay = 0)

    // In-memory buffer to store table names before flushing
    private val pendingUpdates = mutableSetOf<String>()

    @Suppress("unused")
    fun updateTableHook(tableName: String) {
        pendingUpdates.add(tableName)

        scope.launch {
            fireTableUpdates()
        }
    }

    // Function to register for table updates
    fun registerForUpdates(collector: suspend (List<String>) -> Unit) {
        scope.launch {
            tableUpdatesFlow.collect { collector(it) }
        }
    }

    fun registerForUpdatesOnTable(tableName: String, collector: suspend () -> Unit) {
        scope.launch {
            tableUpdatesFlow.filter { tables ->
                tables.contains(tableName)
            }.collect { collector() }
        }
    }

    private suspend fun fireTableUpdates() {
        if (pendingUpdates.isNotEmpty()) {
            tableUpdatesFlow.emit(pendingUpdates.toList())
            pendingUpdates.clear()
        }
    }
}