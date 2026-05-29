package com.powersync.db.internal

import com.powersync.db.SqlCursor
import com.powersync.db.driver.SQLiteConnectionLease

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual interface ConnectionContext {
    public actual suspend fun executeAsync(
        sql: String,
        parameters: List<Any?>?,
    ): Long

    public actual suspend fun <RowType : Any> getOptionalAsync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType?

    public actual suspend fun <RowType : Any> getAllAsync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType>

    public actual suspend fun <RowType : Any> getAsync(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType
}

internal actual fun SQLiteConnectionLease.asContext(): ConnectionContext = LeaseContext(this)

private class LeaseContext(
    rawConnection: SQLiteConnectionLease,
) : BaseConnectionContextImplementation(
        rawConnection,
    )
