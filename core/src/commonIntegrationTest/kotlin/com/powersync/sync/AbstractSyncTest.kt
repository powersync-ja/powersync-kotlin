package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.testutils.ActiveDatabaseTest

/**
 * Small utility to run tests both with the legacy Kotlin sync implementation and the new
 * implementation from the core extension.
 */
abstract class AbstractSyncTest(
    private val useNewSyncImplementation: Boolean,
) {
    @OptIn(ExperimentalPowerSyncAPI::class)
    internal fun ActiveDatabaseTest.getOptions(): SyncOptions =
        SyncOptions(
            useNewSyncImplementation,
            clientConfiguration = SyncClientConfiguration.ExistingClient(createSyncClient()),
        )
}
