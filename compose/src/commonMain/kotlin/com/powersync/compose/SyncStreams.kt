package com.powersync.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.powersync.PowerSyncDatabase
import com.powersync.bucket.StreamPriority
import com.powersync.sync.SyncStreamStatus
import com.powersync.utils.JsonParam
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * Subscribes to a [com.powersync.sync.SyncStream] as long as the current composition is active.
 *
 * For more details on sync streams, see [the documentation](https://docs.powersync.com/sync/streams/overview).
 */
@Composable
public fun PowerSyncDatabase.composeSyncStream(
    name: String,
    parameters: Map<String, JsonParam>? = null,
    ttl: Duration? = null,
    priority: StreamPriority? = null,
): SyncStreamStatus? {
    val stream = remember(name, parameters) { this.syncStream(name, parameters) }
    val status by currentStatus.composeState()

    LaunchedEffect(stream) {
        val subscription = stream.subscribe(ttl, priority)
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                subscription.unsubscribe()
            }
        }
    }

    return status.forStream(stream)
}
