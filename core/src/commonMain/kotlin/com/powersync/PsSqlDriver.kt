package com.powersync

import app.cash.sqldelight.db.SqlDriver
import com.powersync.utils.AtomicMutableSet
import com.powersync.utils.throttle
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

    // Flows on table updates containing tables
    fun updatesOnTables(tableNames: Set<String>, throttleMs: Long?): Flow<Unit> {
        // Spread the input table names in order to account for internal views
        val resolvedTableNames =
            tableNames
                .flatMap { t -> setOf("ps_data__$t", "ps_data_local__$t", t) }
                .toSet()
        var flow = tableUpdatesFlow
            .asSharedFlow()
            .filter {
                it
                    .intersect(
                        resolvedTableNames,
                    ).isNotEmpty()
            }

        if (throttleMs != null) {
            flow = flow.throttle(throttleMs)
        }

        return flow.map { }
    }

    fun fireTableUpdates() {
        scope.launch {
            tableUpdatesFlow.emit(pendingUpdates.toSet(true))
        }
    }
}
