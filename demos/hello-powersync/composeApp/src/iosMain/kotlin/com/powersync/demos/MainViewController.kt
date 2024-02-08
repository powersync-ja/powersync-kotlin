package com.powersync.demos

import androidx.compose.ui.window.ComposeUIViewController
import com.powersync.DatabaseDriverFactory

fun MainViewController() = ComposeUIViewController { App(PowerSync(DatabaseDriverFactory())) }
