package com.powersync.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.db.schema.Schema
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    App(openPowerSync = ::openPowerSyncDatabase, modifier = Modifier.fillMaxSize())
}

private fun openPowerSyncDatabase(schema: Schema): PowerSyncDatabase {
    return PowerSyncDatabase(DatabaseDriverFactory(), schema)
}
