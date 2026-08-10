package com.powersync.demos.components

import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import com.powersync.sync.SyncStatusData
import org.jetbrains.compose.resources.painterResource
import powersync_root.demos.supabase_todolist.shared.generated.resources.Res
import powersync_root.demos.supabase_todolist.shared.generated.resources.cloud
import powersync_root.demos.supabase_todolist.shared.generated.resources.cloud_off
import powersync_root.demos.supabase_todolist.shared.generated.resources.cloud_sync
import powersync_root.demos.supabase_todolist.shared.generated.resources.leak_add
import powersync_root.demos.supabase_todolist.shared.generated.resources.thunderstorm

@Composable
fun WifiIcon(status: SyncStatusData) {
    val icon =
        when {
            status.downloading || status.uploading -> Res.drawable.cloud_sync
            status.connected -> Res.drawable.cloud
            !status.connected -> Res.drawable.cloud_off
            status.connecting -> Res.drawable.leak_add
            else -> {
                Res.drawable.thunderstorm
            }
        }

    Icon(
        painter = painterResource(icon),
        contentDescription = status.toString(),
    )
}
