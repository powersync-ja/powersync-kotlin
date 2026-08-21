package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.testutils.ActiveDatabaseTest
import kotlinx.coroutines.flow.first

/**
 * Small utility to run tests both with the legacy Kotlin sync implementation and the new
 * implementation from the core extension.
 */
abstract class AbstractSyncTest(
    protected val useBson: Boolean = false,
) {
    protected suspend fun PowerSyncDatabase.waitForStatus(predicate: (SyncStatusData) -> Boolean) {
        this.currentStatus.asFlow().first { status ->
            if (predicate(status)) return@first true

            status.anyError?.let { error("Unexpected error in $status") }
            false
        }
    }

    @OptIn(ExperimentalPowerSyncAPI::class)
    internal fun ActiveDatabaseTest.getOptions(): SyncOptions =
        SyncOptions(
            clientConfiguration = SyncClientConfiguration.ExistingClient(createSyncClient()),
        )
}
