package com.powersync.db.internal

import app.cash.sqldelight.db.Closeable
import com.persistence.PowersyncQueries
import com.powersync.db.Queries
import com.powersync.persistence.PsDatabase
import kotlinx.coroutines.flow.Flow

internal interface InternalDatabase :
    Queries,
    Closeable {
    val transactor: PsDatabase
    val queries: PowersyncQueries

    fun updatesOnTables(tableNames: Set<String>): Flow<Unit>
}
