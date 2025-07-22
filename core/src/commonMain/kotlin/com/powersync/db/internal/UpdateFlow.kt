package com.powersync.db.internal

import com.powersync.internal.driver.ConnectionListener
import com.powersync.utils.AtomicMutableSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class UpdateFlow: ConnectionListener {
    // MutableSharedFlow to emit batched table updates
    private val tableUpdatesFlow = MutableSharedFlow<Set<String>>(replay = 0)

    // In-memory buffer to store table names before flushing
    private val pendingUpdates = AtomicMutableSet<String>()

    override fun onCommit() {}

    override fun onRollback() {
        pendingUpdates.clear()
    }

    override fun onUpdate(
        kind: Int,
        database: String,
        table: String,
        rowid: Long
    ) {
        pendingUpdates.add(table)
    }

    // Flows on any table change
    // This specifically returns a SharedFlow for downstream timing considerations
    fun updatesOnTables(): SharedFlow<Set<String>> =
        tableUpdatesFlow
            .asSharedFlow()

    suspend fun fireTableUpdates() {
        val updates = pendingUpdates.toSetAndClear()
        tableUpdatesFlow.emit(updates)
    }
}
