package com.powersync

import app.cash.sqldelight.db.SqlCursor
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.db.internal.InternalDatabaseImpl

internal fun PowerSyncTransaction(
    internalDatabase: InternalDatabaseImpl,
): PowerSyncTransaction {
    val transaction = object : PowerSyncTransaction {

        override suspend fun execute(sql: String, parameters: List<Any?>?): Long {
            return internalDatabase.execute(sql, parameters ?: emptyList())
        }

        override suspend fun <RowType : Any> get(
            sql: String,
            parameters: List<Any?>?,
            mapper: (SqlCursor) -> RowType
        ): RowType {
            return internalDatabase.get(sql, parameters ?: emptyList(), mapper)
        }

        override suspend fun <RowType : Any> getAll(
            sql: String,
            parameters: List<Any?>?,
            mapper: (SqlCursor) -> RowType
        ): List<RowType> {
            return internalDatabase.getAll(sql, parameters ?: emptyList(), mapper)
        }
         override suspend fun <RowType : Any> getOptional(
             sql: String,
             parameters: List<Any?>?,
             mapper: (SqlCursor) -> RowType
         ): RowType? {
             return internalDatabase.getOptional(sql, parameters ?: emptyList(), mapper)
         }
    }

    return transaction
}