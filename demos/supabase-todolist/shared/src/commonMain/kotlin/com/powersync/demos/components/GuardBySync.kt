package com.powersync.demos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.powersync.PowerSyncDatabase
import com.powersync.bucket.StreamPriority
import com.powersync.compose.composeState
import com.powersync.sync.SyncStatusData
import org.koin.compose.koinInject

/**
 * A component that renders its [content] only after a first complete sync was completed on [db].
 *
 * Before that, a progress indicator is shown instead.
 */
@Composable
fun GuardBySync(
    db: PowerSyncDatabase = koinInject(),
    priority: StreamPriority? = null,
    content: @Composable () -> Unit
) {
    val state: SyncStatusData by db.currentStatus.composeState()

    if (state.hasSynced == true) {
        content()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // When we have no hasSynced information, the database is still being opened. We just show a
        // generic progress bar in that case.
        val databaseOpening = state.hasSynced == null

        if (!databaseOpening) {
            Text(
                text = "Busy with initial sync...",
                style = MaterialTheme.typography.h6,
            )
        }

        val progress = state.downloadProgress?.let {
            if (priority == null) {
                it
            } else {
                it.untilPriority(priority)
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                progress = progress.fraction,
            )

            if (progress.downloadedOperations == progress.totalOperations) {
                Text("Applying server-side changes...")
            } else {
                Text("Downloaded ${progress.downloadedOperations} out of ${progress.totalOperations}.")
            }
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }
    }
}
