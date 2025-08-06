package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Configuration options for the [PowerSyncDatabase.connect] method, allowing customization of
 * the HTTP client used to connect to the PowerSync service.
 */
@OptIn(ExperimentalObjCRefinement::class)
public sealed class SyncClientConfiguration {
    /**
     * Extends the default Ktor [HttpClient] configuration with the provided block.
     */
    @HiddenFromObjC
    public class ExtendedConfig(
        public val block: HttpClientConfig<*>.() -> Unit,
    ) : SyncClientConfiguration()

    /**
     * Provides an existing [HttpClient] instance to use for connecting to the PowerSync service.
     * This client should be configured with the necessary plugins and settings to function correctly.
     * The HTTP client requirements are delicate and subject to change throughout the SDK's development.
     * The [configureSyncHttpClient] function can be used to configure the client for PowerSync.
     */
    @HiddenFromObjC
    @ExperimentalPowerSyncAPI
    public class ExistingClient(
        public val client: HttpClient,
    ) : SyncClientConfiguration()
}

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
        @property:ExperimentalPowerSyncAPI
        /**
         * Allows configuring the [HttpClient] used for connecting to the PowerSync service.
         */
        public val clientConfiguration: SyncClientConfiguration? = null,
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
