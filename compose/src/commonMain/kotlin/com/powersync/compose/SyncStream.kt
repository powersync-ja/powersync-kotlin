package com.powersync.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.bucket.StreamPriority
import com.powersync.sync.SyncStreamStatus
import com.powersync.sync.SyncStreamSubscription
import com.powersync.utils.JsonParam
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * Creates a PowerSync stream subscription. The subscription is kept alive as long as this
 * composable. When the composition is left, [SyncStreamSubscription.unsubscribe] is called
 *
 * For more details on sync streams, see the [documentation](https://docs.powersync.com/usage/sync-streams).
 *
 * @returns The status for that stream, or `null` if the stream is currently being resolved.
 */
@ExperimentalPowerSyncAPI
@Composable
public fun PowerSyncDatabase.composeSyncStream(
    name: String,
    parameters: Map<String, JsonParam>? = null,
    ttl: Duration? = null,
    priority: StreamPriority? = null,
): SyncStreamStatus? {
    val syncStatus by currentStatus.composeState()
    val (resolvedHandle, changeHandle) = remember { mutableStateOf<SyncStreamSubscription?>(null) }

    LaunchedEffect(name, parameters) {
        var sub: SyncStreamSubscription? = null
        try {
            sub = syncStream(name, parameters).subscribe(ttl, priority)
            changeHandle(sub)
            // Wait for the composable to unmount
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                sub?.unsubscribe()
            }

            changeHandle(null)
        }
    }

    return resolvedHandle?.let { syncStatus.forStream(it) }
}
