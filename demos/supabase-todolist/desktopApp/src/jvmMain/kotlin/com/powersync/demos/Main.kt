package com.powersync.demos

import MainView
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter


fun main() {
    Logger.setLogWriters(platformLogWriter())
    Logger.setMinSeverity(Severity.Verbose)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "TodoApp Lite",
            state = rememberWindowState(
                position = WindowPosition(alignment = Alignment.Center),
            ),
        ) {
            MaterialTheme {
                MainView()
            }
        }
    }
}
