package com.powersync

import app.cash.sqldelight.db.SqlDriver
import co.touchlab.kermit.Logger
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
    private val otherTableUpdatesFlow =
        MutableSharedFlow<List<String>>(replay = 0, extraBufferCapacity = 64)

    private val uiTableUpdatesFlow =
        MutableSharedFlow<List<String>>(replay = 0, extraBufferCapacity = 64)

    private val crudTableUpdatesFlow =
        MutableSharedFlow<List<String>>(replay = 0, extraBufferCapacity = 64)

    // In-memory buffer to store table names before flushing
    private val pendingUpdates = mutableSetOf<String>()

    fun updateTable(tableName: String) {
        pendingUpdates.add(tableName)
    }

    fun clearTableUpdates() {
        pendingUpdates.clear()
    }

    fun crudTableUpdates(): Flow<List<String>> = crudTableUpdatesFlow.asSharedFlow()

    fun updatesOnCrudTable(tableName: String): Flow<Unit> = crudTableUpdates().filter { it.contains(tableName) }.map { }

    fun uiTableUpdates(): Flow<List<String>> = uiTableUpdatesFlow.asSharedFlow()

    fun otherTableUpdates(): Flow<List<String>> = otherTableUpdatesFlow.asSharedFlow()

    fun fireTableUpdates() {
        val updates = pendingUpdates.toList()
        if (updates.isEmpty()) {
            return
        }
        val otherUpdates = updates.filter { !isDataTable(it) && !isCrudTable(it) }
        val uiUpdates = updates.filter { isDataTable(it) }
        val crudUpdates = updates.filter { isCrudTable(it) }

        if (!otherTableUpdatesFlow.tryEmit(otherUpdates)) {
            Logger.i("Failed to emit other table updates")
        } else {
            Logger.i(("RUNNING OTHER UPDATES"))
            Logger.i(otherUpdates.toString())
        }

        if (!crudTableUpdatesFlow.tryEmit(crudUpdates)) {
            Logger.i("Failed to emit CRUD table updates will try normal emit and suspend")
            scope.launch {
                crudTableUpdatesFlow.emit(crudUpdates)
            }
        } else if (crudUpdates.isNotEmpty()) {
            Logger.i(("RUNNING CRUD UPDATES"))
            Logger.i(crudUpdates.toString())
        }

        if (!uiTableUpdatesFlow.tryEmit(uiUpdates)) {
            Logger.i("Failed to emit ui table updates")
        } else if (uiUpdates.isNotEmpty()) {
            Logger.i(("RUNNING UI UPDATES"))
            Logger.i(uiUpdates.toString())
        }

        pendingUpdates.clear()
    }

    // Determine if a table requires high-priority processing
    private fun isDataTable(tableName: String): Boolean = tableName.startsWith("ps_data")

    private fun isCrudTable(tableName: String): Boolean = tableName.startsWith("ps_crud")
}
