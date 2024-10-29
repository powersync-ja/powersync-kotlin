package com.powersync

import app.cash.sqldelight.db.SqlCursor
import com.powersync.db.internal.InternalDatabaseImpl
import com.powersync.db.internal.PowerSyncTransaction

internal fun PowerSyncTransaction(internalDatabase: InternalDatabaseImpl): PowerSyncTransaction {
    val transaction =
        object : PowerSyncTransaction {
            override suspend fun execute(
                sql: String,
                parameters: List<Any?>?,
            ): Long = internalDatabase.execute(sql, parameters ?: emptyList())

            override suspend fun <RowType : Any> get(
                sql: String,
                parameters: List<Any?>?,
                mapper: (SqlCursor) -> RowType,
            ): RowType = internalDatabase.get(sql, parameters ?: emptyList(), mapper)

            override suspend fun <RowType : Any> getAll(
                sql: String,
                parameters: List<Any?>?,
                mapper: (SqlCursor) -> RowType,
            ): List<RowType> = internalDatabase.getAll(sql, parameters ?: emptyList(), mapper)

            override suspend fun <RowType : Any> getOptional(
                sql: String,
                parameters: List<Any?>?,
                mapper: (SqlCursor) -> RowType,
            ): RowType? = internalDatabase.getOptional(sql, parameters ?: emptyList(), mapper)
        }

    return transaction
}
