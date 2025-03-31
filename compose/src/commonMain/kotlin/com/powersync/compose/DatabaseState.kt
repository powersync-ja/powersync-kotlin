package com.powersync.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import com.powersync.sync.SyncStatus
import com.powersync.sync.SyncStatusData
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration

@OptIn(FlowPreview::class)
@Composable
public fun SyncStatus.composeState(debounce: Duration=Duration.ZERO): State<SyncStatusData> {
    var flow: Flow<SyncStatusData> = asFlow()
    if (debounce.isPositive()) {
        flow = flow.debounce(debounce)
    }

    return flow.collectAsState(initial = this)
}
