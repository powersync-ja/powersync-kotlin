package com.powersync.demo.self_host.powersync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.powersync.PowerSyncDatabase
import com.powersync.integrations.sqldelight.PowerSyncDriver
import com.powersync.integrations.sqldelight.TodoDatabase

val PowerSync = compositionLocalOf<PowerSyncDatabase?> { null }
val Database = compositionLocalOf<TodoDatabase?> { null }

@Composable
fun WithDatabase(
    powersync: PowerSyncDatabase,
    inner: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sqlDelight = remember(powersync) { TodoDatabase(PowerSyncDriver(powersync, scope)) }

    CompositionLocalProvider(PowerSync provides powersync, Database provides sqlDelight) {
        inner()
    }
}

@Composable
fun usePowerSync(): PowerSyncDatabase = PowerSync.current!!

@Composable
fun useTodoDatabase(): TodoDatabase = Database.current!!
