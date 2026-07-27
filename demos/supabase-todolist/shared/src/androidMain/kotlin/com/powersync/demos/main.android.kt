package com.powersync.demos

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase

@Composable
fun MainView(factory: DatabaseDriverFactory) = App(
    { schema -> PowerSyncDatabase(factory, schema) },
    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
)
