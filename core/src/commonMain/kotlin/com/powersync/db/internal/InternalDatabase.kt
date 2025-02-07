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

    fun getExistingTableNames(tableGlob: String): List<String>

    fun updatesOnTable(tableName: String): Flow<Unit>
}
