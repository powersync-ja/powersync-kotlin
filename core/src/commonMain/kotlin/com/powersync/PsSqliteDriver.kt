package com.powersync

import app.cash.sqldelight.db.SqlDriver

class PsSqliteDriver(private val driver: SqlDriver) : SqlDriver by driver {
    // TODO: Future work - Use Coroutine Flows and/or Channels
    private val tableUpdateListeners: MutableMap<String, MutableList<() -> Unit>> = mutableMapOf()

    @Suppress("unused")
    fun updateHook(
        operationType: Int,
        databaseName: String,
        tableName: String,
        rowId: Long
    ) {
        tableUpdateListeners[tableName]?.forEach { it() }
    }

    fun tableUpdates(tableName: String, callback: () -> Unit): () -> Unit {
        tableUpdateListeners.getOrPut(tableName) { mutableListOf() }.add(callback)

        return {
            tableUpdateListeners[tableName]?.remove(callback)
        }
    }
}