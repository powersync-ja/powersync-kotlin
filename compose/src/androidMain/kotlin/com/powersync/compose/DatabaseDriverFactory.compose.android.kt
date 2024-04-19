package com.powersync.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.powersync.DatabaseDriverFactory


@Composable
public actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory {
    val context = LocalContext.current
    return remember {
        DatabaseDriverFactory(context)
    }

}
