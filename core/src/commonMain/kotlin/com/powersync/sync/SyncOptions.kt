package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase

/**
 * Experimental options that can be passed to [PowerSyncDatabase.connect] to specify an experimental
 * connection mechanism.
 *
 * The new connection implementation is more efficient and we expect it to become the default in
 * the future. At the moment, the implementation is not covered by the stability guarantees we offer
 * for the rest of the SDK though.
 */
public class SyncOptions
    @ExperimentalPowerSyncAPI
    constructor(
        @property:ExperimentalPowerSyncAPI
        public val newClientImplementation: Boolean = false,
        /**
         * The user agent to use for requests made to the PowerSync service.
         */
        public val userAgent: String = userAgent(),
    ) {
        public companion object {
            /**
             * The default sync options, which are safe and stable to use.
             *
             * Constructing non-standard sync options requires an opt-in to experimental PowerSync
             * APIs, and those might change in the future.
             */
            @OptIn(ExperimentalPowerSyncAPI::class)
            public val defaults: SyncOptions = SyncOptions()
        }
    }
