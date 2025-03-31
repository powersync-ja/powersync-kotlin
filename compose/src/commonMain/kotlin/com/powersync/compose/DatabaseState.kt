package com.powersync.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import com.powersync.sync.SyncStatus
import com.powersync.sync.SyncStatusData
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Composable
public fun SyncStatus.composeState(debounce: Duration=200.0.milliseconds): State<SyncStatusData> = asFlow()
    // Debouncing the status flow prevents flicker
    .debounce(debounce)
    .collectAsState(initial = this)
