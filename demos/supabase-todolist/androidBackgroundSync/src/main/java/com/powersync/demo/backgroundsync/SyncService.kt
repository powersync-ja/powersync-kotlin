package com.powersync.demo.backgroundsync

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import com.powersync.sync.SyncStatusData
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SyncService: LifecycleService() {

    private val connector: SupabaseConnector by inject()
    private val database: PowerSyncDatabase by inject()

    private val notificationManager get()= NotificationManagerCompat.from(this)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        createNotificationChannel()

        ServiceCompat.startForeground(
            this,
            startId,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )

        lifecycleScope.launch {
            database.currentStatus.asFlow().collect {
                try {
                    notificationManager.notify(startId, buildNotification(it))
                } catch (e: SecurityException) {
                    Logger.d("Ignoring security exception when updating notification", e)
                }
            }
        }

        lifecycleScope.launch {
            connector.sessionStatus.collect {
                when (it) {
                    is SessionStatus.Authenticated -> {
                        database.connect(connector)
                    }
                    is SessionStatus.NotAuthenticated -> {
                        database.disconnectAndClear()
                        Logger.i("Stopping sync service, user logged out")
                        return@collect
                    }
                    else -> {
                        // Ignore
                    }
                }
            }
        }.invokeOnCompletion {
            if (it !is CancellationException) {
                this.lifecycle.currentState
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Background sync was running for too long without the app ever being open...
        stopSelf(startId)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.background_channel_name))
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: SyncStatusData? = null): Notification = Notification.Builder(this, CHANNEL_ID).apply {
        setContentTitle(getString(R.string.sync_notification_title))
        setSmallIcon(R.drawable.ic_launcher_foreground)

        if (state != null) {
            if (state.uploading || state.downloading) {
                setProgress(0, 0, true)
            }
        }
    }.build()

    private companion object {
        private val CHANNEL_ID = "background_sync"
    }
}
