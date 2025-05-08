package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI

abstract class AbstractSyncTest(private val useNewSyncImplementation: Boolean) {

    @OptIn(ExperimentalPowerSyncAPI::class)
    val options: SyncOptions get() {
        return SyncOptions(useNewSyncImplementation)
    }
}

