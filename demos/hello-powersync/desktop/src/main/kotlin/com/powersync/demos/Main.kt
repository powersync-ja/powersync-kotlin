package com.powersync.demos

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1200.dp, 900.dp))

    Window(state = windowState, onCloseRequest = ::exitApplication, title = "PowerSync Demo") {
        App()
    }
}