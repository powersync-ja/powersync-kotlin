package com.powersync.db.internal

import app.cash.sqldelight.db.Closeable
import com.powersync.db.Queries
import kotlinx.coroutines.flow.SharedFlow

internal interface InternalDatabase :
    Queries,
    Closeable {
    fun updatesOnTables(): SharedFlow<Set<String>>
}
