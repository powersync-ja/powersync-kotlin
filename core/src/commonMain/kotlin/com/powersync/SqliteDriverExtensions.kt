package com.powersync

import kotlin.jvm.JvmName
import app.cash.sqldelight.db.SqlDriver

@JvmName("updateHook")
fun SqlDriver.updateHook(operationType: Int, databaseName: String, tableName: String, rowId: Long) {
    notifyListeners(databaseName)
}