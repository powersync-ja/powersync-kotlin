package com.powersync

import app.cash.sqldelight.db.SqlDriver
import com.powersync.utils.AtomicMutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class PsSqlDriver(
    private val driver: SqlDriver,
    private val scope: CoroutineScope,
) : SqlDriver by driver {
    // MutableSharedFlow to emit batched table updates
    private val tableUpdatesFlow = MutableSharedFlow<Set<String>>(replay = 0)

    // In-memory buffer to store table names before flushing
    private val pendingUpdates = AtomicMutableSet<String>()

    fun updateTable(tableName: String) {
        pendingUpdates.add(tableName)
    }

    fun clearTableUpdates() {
        pendingUpdates.clear()
    }

    // Flows on any table change
    // This specifically returns a SharedFlow for downstream timing considerations
    fun updatesOnTables(): SharedFlow<Set<String>> =
        tableUpdatesFlow
            .asSharedFlow()

    suspend fun fireTableUpdates() {
        tableUpdatesFlow.emit(pendingUpdates.toSetAndClear())
    }
}
