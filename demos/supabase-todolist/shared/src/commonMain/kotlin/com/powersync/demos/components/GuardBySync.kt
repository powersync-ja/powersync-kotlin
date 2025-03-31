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
import com.powersync.compose.composeState
import org.koin.compose.koinInject

/**
 * A component that renders its [content] only after a first complete sync was completed on [db].
 *
 * Before that, a progress indicator is shown instead.
 */
@Composable
fun GuardBySync(
    db: PowerSyncDatabase = koinInject(),
    content: @Composable () -> Unit
) {
    val state by db.currentStatus.composeState()

    if (state.hasSynced == true) {
        content()
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Busy with initial sync...",
            style = MaterialTheme.typography.h6,
        )

        val progress = state.downloadProgress?.untilCompletion
        if (progress != null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                progress = progress.fraction,
            )

            if (progress.total == progress.completed) {
                Text("Applying server-side changes...")
            } else {
                Text("Downloaded ${progress.completed} out of ${progress.total}.")
            }
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }
    }
}
