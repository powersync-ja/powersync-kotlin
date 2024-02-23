package com.powersync

import kotlinx.coroutines.CoroutineScope

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class DatabaseDriverFactory {

    fun createDriver(
        scope: CoroutineScope,
        dbFilename: String
    ): PsSqliteDriver
}