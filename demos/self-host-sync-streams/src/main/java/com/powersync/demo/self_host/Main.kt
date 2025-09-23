package com.powersync.demo.self_host

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import co.touchlab.kermit.platformLogWriter
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.compose.composeSyncStream
import com.powersync.demo.self_host.powersync.DemoConnector
import com.powersync.demo.self_host.powersync.WithDatabase
import com.powersync.demo.self_host.powersync.schema
import com.powersync.demo.self_host.powersync.usePowerSync
import com.powersync.demo.self_host.views.Lists
import com.powersync.demo.self_host.views.Todos
import com.powersync.integrations.sqldelight.Lists
import com.powersync.sync.SyncOptions

fun main() {
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Self-hosted todo app",
            state =
                rememberWindowState(
                    position = WindowPosition(alignment = Alignment.Center),
                ),
        ) {
            MaterialTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val logger =
        remember {
            Logger(
                loggerConfigInit(
                    platformLogWriter(),
                    minSeverity = Severity.Verbose,
                ),
            )
        }
    val database =
        remember {
            PowerSyncDatabase(
                DatabaseDriverFactory(),
                schema,
                dbFilename = "self_host_powersync.db",
                logger = logger,
            )
        }
    LaunchedEffect(Unit) {
        database.connect(DemoConnector(logger), options = SyncOptions(newClientImplementation = true))
    }

    WithDatabase(database) {
        var selectedList by remember { mutableStateOf<Lists?>(null) }
        val stream = usePowerSync().composeSyncStream("lists")

        if (stream?.subscription?.hasSynced == true) {
            selectedList?.let { Todos(it, onBack = { selectedList = null }) } ?: Lists(onItemClicked = { list -> selectedList = list })
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }
    }
}
