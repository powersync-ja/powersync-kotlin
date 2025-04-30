package com.powersync.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import com.powersync.sync.SyncStatus
import com.powersync.sync.SyncStatusData

@Composable
public fun SyncStatus.composeState(): State<SyncStatusData> = asFlow().collectAsState(initial = this)
