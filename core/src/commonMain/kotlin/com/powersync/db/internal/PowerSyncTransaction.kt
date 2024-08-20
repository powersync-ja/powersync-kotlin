package com.powersync.db.internal

import app.cash.sqldelight.db.SqlCursor

public interface PowerSyncTransaction {
    public suspend fun execute(sql: String, parameters: List<Any?>? = listOf()): Long

    public suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): RowType?

    public suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): List<RowType>

    public suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType
    ): RowType
}
