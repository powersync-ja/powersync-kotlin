package com.powersync

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

val SqlDriver.tableNameFlow: MutableSharedFlow<String>
    get() = MutableSharedFlow()

fun SqlDriver.updateHook(operationType: Int, databaseName: String, tableName: String, rowId: Long) {
    tableNameFlow.tryEmit(tableName)
}

fun SqlDriver.tableUpdates() = tableNameFlow.asSharedFlow()