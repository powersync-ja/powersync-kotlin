package com.powersync.demos.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.powersync.ExperimentalCheckpointRequestsApi
import com.powersync.PowerSyncDatabase
import com.powersync.compose.composeState
import com.powersync.demos.Config
import com.powersync.sync.CheckpointRequest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import powersync_root.demos.supabase_todolist.shared.generated.resources.Res
import powersync_root.demos.supabase_todolist.shared.generated.resources.refresh
import kotlin.coroutines.cancellation.CancellationException

/**
 * A refresh button requesting an explicit sync (via [PowerSyncDatabase.requestCheckpoint]) when
 * pressed.
 */
@OptIn(ExperimentalCheckpointRequestsApi::class)
@Composable
fun RefreshIcon(
    snackBarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    database: PowerSyncDatabase = koinInject(),
) {
    if (!Config.USE_CHECKPOINT_REQUESTS) return

    val scope = rememberCoroutineScope()
    val syncStatus by database.currentStatus.composeState()
    var checkpointRequest by remember { mutableStateOf<CheckpointRequest?>(null) }
    val isSyncing = syncStatus.downloading || checkpointRequest != null

    fun explicitSync() {
        scope.launch {
            val message = try {
                val request = database.requestCheckpoint()
                checkpointRequest = request
                request.waitForSync()
                "Sync request complete!"
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                println("Checkpoint request failed: $e")
                "Sync request failed"
            } finally {
                checkpointRequest = null
            }

            snackBarHostState.showSnackbar(message)
        }
    }

    val rotation by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    IconButton(::explicitSync, modifier, enabled = !isSyncing) {
        Icon(
            painter = painterResource(Res.drawable.refresh),
            contentDescription = "Request explicit sync",
            modifier = Modifier.rotate(if (isSyncing) rotation else 0f),
        )
    }
}
