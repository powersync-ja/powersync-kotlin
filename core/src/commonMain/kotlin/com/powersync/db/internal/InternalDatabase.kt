package com.powersync.db.internal

import com.powersync.db.Queries
import kotlinx.coroutines.flow.SharedFlow

internal interface InternalDatabase : Queries {
    fun updatesOnTables(): SharedFlow<Set<String>>

    suspend fun updateSchema(schemaJson: String): Unit

    suspend fun close(): Unit
}
