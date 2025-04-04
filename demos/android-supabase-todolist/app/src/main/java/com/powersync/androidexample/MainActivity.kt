package com.powersync.androidexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.powersync.androidexample.ui.CameraService
import com.powersync.demos.App

class MainActivity : ComponentActivity() {
    private val cameraService = CameraService(this)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setContent {
            App(
                cameraService = cameraService,
                attachmentDirectory = "${applicationContext.filesDir.canonicalPath}/attachments"
            )
        }
    }

}
