package com.powersync.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.powersync.DatabaseDriverFactory

@Composable
fun MainView(factory: DatabaseDriverFactory) = App(factory, Modifier.fillMaxSize())