package com.powersync.db.internal

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

internal object InternalSchema : SqlSchema<QueryResult.AsyncValue<Unit>> {
    override val version: Long
        get() = 1

    override fun create(driver: SqlDriver): QueryResult.AsyncValue<Unit> =
        QueryResult.AsyncValue {}

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.AsyncValue<Unit> =
        QueryResult.AsyncValue {}
}
