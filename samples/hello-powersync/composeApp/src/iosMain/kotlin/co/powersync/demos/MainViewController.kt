package co.powersync.demos

import androidx.compose.ui.window.ComposeUIViewController
import co.powersync.core.DatabaseDriverFactory

fun MainViewController() = ComposeUIViewController { App(DatabaseDriverFactory()) }
