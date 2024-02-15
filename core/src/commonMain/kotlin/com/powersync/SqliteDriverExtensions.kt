package com.powersync

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

val SqlDriver.tableNameFlow: MutableSharedFlow<String>
    get() = MutableSharedFlow()

val SqlDriver.updateHook: (action: Int, databaseName: String, tableName: String, rowId: Long) -> Unit
    get() = { _, _, tableName, _ ->
        tableNameFlow.tryEmit(tableName)
    }

fun SqlDriver.tableUpdates() = tableNameFlow.asSharedFlow()