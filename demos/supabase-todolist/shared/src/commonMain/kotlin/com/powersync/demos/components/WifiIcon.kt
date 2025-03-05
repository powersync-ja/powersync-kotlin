package com.powersync.demos.components

import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LeakAdd
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.runtime.Composable
import com.powersync.sync.SyncStatusData

@Composable
fun WifiIcon(status: SyncStatusData) {
    val icon =
        when {
            status.downloading || status.uploading -> Icons.Filled.CloudSync
            status.connected -> Icons.Filled.Cloud
            !status.connected -> Icons.Filled.CloudOff
            status.connecting -> Icons.Filled.LeakAdd
            else -> {
                Icons.Filled.Thunderstorm
            }
        }

    Icon(
        imageVector = icon,
        contentDescription = status.toString(),
    )
}
