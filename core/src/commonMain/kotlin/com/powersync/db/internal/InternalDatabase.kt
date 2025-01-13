package com.powersync.db.internal

import com.persistence.PowersyncQueries
import com.powersync.PsSqlDriver
import com.powersync.db.Queries
import com.powersync.persistence.PsDatabase
import kotlinx.coroutines.flow.Flow

internal interface InternalDatabase : Queries {
    val driver: PsSqlDriver
    val transactor: PsDatabase
    val queries: PowersyncQueries

    fun getExistingTableNames(tableGlob: String): List<String>

    fun updatesOnTable(tableName: String): Flow<Unit>

    fun close()
}
