package com.powersync

import kotlinx.coroutines.CoroutineScope

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class DatabaseDriverFactory {
    internal fun createDriver(
        scope: CoroutineScope,
        dbFilename: String,
    ): PsSqlDriver
}
