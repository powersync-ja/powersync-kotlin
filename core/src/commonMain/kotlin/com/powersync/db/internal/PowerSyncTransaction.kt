package com.powersync.db.internal

import app.cash.sqldelight.db.SqlCursor
import co.touchlab.skie.configuration.annotations.SuspendInterop

public interface PowerSyncTransaction {
    @SuspendInterop.Disabled
    public suspend fun execute(
        sql: String,
        parameters: List<Any?>? = listOf(),
    ): Long

    @SuspendInterop.Disabled
    public suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType?

    @SuspendInterop.Disabled
    public suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): List<RowType>

    @SuspendInterop.Disabled
    public suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType
}
