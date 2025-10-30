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
     * The [configureSyncHttpClient] function can be used to configure the client for PowerSync, call
     * this method when instantiating the client. The PowerSync SDK does not modify the provided client.
     */
    @HiddenFromObjC
    public class ExistingClient(
        public val client: HttpClient,
    ) : SyncClientConfiguration()
}

/**
 * Options for [PowerSyncDatabase.connect] to customize the connection mechanism.
 */
public class SyncOptions(
    /**
     * Whether to use a new client implementation written in Rust.
     *
     * The new implementation is more efficient is the default. It can be disabled if there are issues with the new
     * implementation, but we expect to remove the old implementation in a future version of the PowerSync SDK. So if
     * you run into issues with the new client, please share them with us!
     */
    public val newClientImplementation: Boolean = true,
    /**
     * The user agent to use for requests made to the PowerSync service.
     */
    public val userAgent: String = userAgent(),
    /**
     * Allows configuring the [HttpClient] used for connecting to the PowerSync service.
     */
    public val clientConfiguration: SyncClientConfiguration? = null,
    /**
     * Whether streams that have been defined with `auto_subscribe: true` should be synced even
     * when they don't have an explicit subscription.
     */
    public val includeDefaultStreams: Boolean = true,
) {
    public companion object {
        /**
         * The default sync options, which are safe and stable to use.
         *
         * Constructing non-standard sync options requires an opt-in to experimental PowerSync
         * APIs, and those might change in the future.
         */
        public val defaults: SyncOptions = SyncOptions()
    }
}
