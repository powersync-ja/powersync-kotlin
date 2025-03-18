package com.powersync.demo.backgroundsync

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.powersync.connector.supabase.SupabaseConnector
import com.powersync.demos.AppContent
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.compose.KoinContext

class MainActivity : ComponentActivity() {

    private val connector: SupabaseConnector by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            // Watch the authentication state and start a sync foreground service once the user logs
            // in.
            connector.sessionStatus.collect {
                if (it is SessionStatus.Authenticated) {
                    startForegroundService(Intent().apply {
                        setClass(this@MainActivity, SyncService::class.java)
                    })
                }
            }
        }

        setContent {
            // We've already started Koin from our application class to be able to use the database
            // outside of the UI here. So, use KoinContext and AppContent instead of the App()
            // composable that would set up its own context.
            KoinContext {
                MaterialTheme {
                    Surface(color = MaterialTheme.colors.background) {
                        AppContent(
                            modifier=Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
                        )
                    }
                }
            }
        }
    }
}
