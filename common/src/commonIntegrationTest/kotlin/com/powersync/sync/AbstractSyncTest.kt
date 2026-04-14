package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.testutils.ActiveDatabaseTest

/**
 * Small utility to run tests both with the legacy Kotlin sync implementation and the new
 * implementation from the core extension.
 */
abstract class AbstractSyncTest(
    protected val useBson: Boolean = false,
) {
    @OptIn(ExperimentalPowerSyncAPI::class)
    internal fun ActiveDatabaseTest.getOptions(): SyncOptions =
        SyncOptions(
            clientConfiguration = SyncClientConfiguration.ExistingClient(createSyncClient()),
        )
}
