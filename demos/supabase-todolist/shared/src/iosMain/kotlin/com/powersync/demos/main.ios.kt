package com.powersync.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.powersync.DatabaseDriverFactory
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    RootContent(factory = DatabaseDriverFactory(), modifier = Modifier.fillMaxSize())
}