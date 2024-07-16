package com.powersync.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.powersync.DatabaseDriverFactory


@Composable
public actual fun rememberDatabaseDriverFactory(): DatabaseDriverFactory {
    return remember {
        DatabaseDriverFactory()
    }
}
