package com.powersync.db.internal

import com.powersync.PowerSyncException
import com.powersync.db.SqlCursor
import kotlin.coroutines.cancellation.CancellationException

public interface ConnectionContext {
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun execute(
        sql: String,
        parameters: List<Any?>? = listOf(),
    ): Long

    @Throws(PowerSyncException::class, CancellationException::class)
    public fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType?

    @Throws(PowerSyncException::class, CancellationException::class)
    public fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): List<RowType>

    @Throws(PowerSyncException::class, CancellationException::class)
    public fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>? = listOf(),
        mapper: (SqlCursor) -> RowType,
    ): RowType
}
