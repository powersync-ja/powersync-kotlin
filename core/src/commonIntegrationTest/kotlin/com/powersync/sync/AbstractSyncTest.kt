package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI

/**
 * Small utility to run tests both with the legacy Kotlin sync implementation and the new
 * implementation from the core extension.
 */
abstract class AbstractSyncTest(
    private val useNewSyncImplementation: Boolean,
) {
    @OptIn(ExperimentalPowerSyncAPI::class)
    val options: SyncOptions get() {
        return SyncOptions(useNewSyncImplementation)
    }
}
