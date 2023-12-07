package co.powersync.demos

import androidx.compose.ui.window.ComposeUIViewController
import co.powersync.DatabaseDriverFactory

fun MainViewController() = ComposeUIViewController { App(DatabaseDriverFactory()) }
