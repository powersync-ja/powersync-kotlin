package com.powersync.db

import com.powersync.PowerSyncDatabase
import com.powersync.utils.ExclusiveMethodProvider

internal class ActiveInstanceStore : ExclusiveMethodProvider() {
    private val instances = mutableListOf<PowerSyncDatabase>()

    /**
     * Registers an instance. Returns true if multiple instances with the same identifier are
     * present.
     */
    suspend fun registerAndCheckInstance(db: PowerSyncDatabase) =
        exclusiveMethod("instances") {
            instances.add(db)
            return@exclusiveMethod instances.filter { it.identifier == db.identifier }.size > 1
        }

    suspend fun removeInstance(db: PowerSyncDatabase) =
        exclusiveMethod("instances") {
            instances.remove(db)
        }
}
