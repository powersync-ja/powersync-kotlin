package com.powersync

import app.cash.sqldelight.db.SqlDriver
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

internal class PsSqlDriver(
    private val driver: SqlDriver,
    private val scope: CoroutineScope,
) : SqlDriver by driver {
    // MutableSharedFlow to emit batched table updates
    private val tableUpdatesFlow =
        MutableSharedFlow<List<String>>(replay = 0, extraBufferCapacity = 64)

    // In-memory buffer to store table names before flushing
    private val pendingUpdates = mutableSetOf<String>()

    fun updateTable(tableName: String) {
        pendingUpdates.add(tableName)
    }

    fun clearTableUpdates() {
        pendingUpdates.clear()
    }

    // Flows on table updates
    fun tableUpdates(): Flow<List<String>> = tableUpdatesFlow.asSharedFlow()

    // Flows on table updates containing a specific table
    fun updatesOnTable(tableName: String): Flow<Unit> = tableUpdates().filter { it.contains(tableName) }.map { }

    fun fireTableUpdates() {
        val updates = pendingUpdates.toList()
        if (updates.isEmpty()) {
            return
        }
        Logger.i(pendingUpdates.toString())
        if (!tableUpdatesFlow.tryEmit(updates)) {
            Logger.i("Failed to emit table updates")
        }

        pendingUpdates.clear()
    }
}
