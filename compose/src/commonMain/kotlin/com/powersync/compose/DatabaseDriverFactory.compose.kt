package com.powersync.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncBuilder
import com.powersync.PowerSyncDatabase
import com.powersync.db.schema.Schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus


@Composable
public expect fun rememberDatabaseDriverFactory(): DatabaseDriverFactory

@Composable
public fun rememberPowerSyncDatabase(schema: Schema): PowerSyncDatabase {
    val driverFactory = rememberDatabaseDriverFactory()
    val scope = rememberCoroutineScope()
    return remember(schema) {
        PowerSyncBuilder
            .from(driverFactory, schema)
            .scope(scope + Dispatchers.Default)
            .build()
    }
}
