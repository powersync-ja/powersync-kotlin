package com.powersync.sync

import com.powersync.ExperimentalCheckpointRequestsApi
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration options for the [PowerSyncDatabase.connect] method, allowing customization of
 * the HTTP client used to connect to the PowerSync service.
 */
public sealed class SyncClientConfiguration {
    /**
     * Extends the default Ktor [HttpClient] configuration with the provided block.
     */
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
    public class ExistingClient(
        public val client: HttpClient,
    ) : SyncClientConfiguration()
}

/**
 * Options for [PowerSyncDatabase.connect] to customize the connection mechanism.
 */
public class SyncOptions(
    /**
     * Enables the new client implementation written in Rust.
     *
     * This is enabled by default, and can no longer be disabled (the old Kotlin client has been
     * removed from the PowerSync SDK).
     */
    public val newClientImplementation: Boolean = true,
    /**
     * The user agent to use for requests made to the PowerSync service.
     */
    public val userAgent: String = userAgent(),
    /**
     * Allows configuring the [HttpClient] used for connecting to the PowerSync service.
     */
    public val clientConfiguration: SyncClientConfiguration = SyncClientConfiguration.ExtendedConfig {},
    /**
     * Whether streams that have been defined with `auto_subscribe: true` should be synced even
     * when they don't have an explicit subscription.
     */
    public val includeDefaultStreams: Boolean = true,
    /**
     * The mode used to request checkpoint requests from the PowerSync service.
     */
    public val checkpointMode: CheckpointMode = CheckpointMode.Legacy,
) {
    public companion object {
        /**
         * The default sync options, which are safe and stable to use.
         */
        @Deprecated("Customizing sync options is no longer necessary, use constructor instead", replaceWith = ReplaceWith("SyncOptions()"))
        public val defaults: SyncOptions = SyncOptions()
    }

    init {
        check(newClientImplementation) {
            "Support for newClientImplementation = false has been removed"
        }
    }
}

/**
 * The mechanism used to request checkpoints from the PowerSync service.
 *
 * Checkpoint requests are used after a client uploads local mutations (or when explicitly requested
 * on the database). The PowerSync service later references them in downloaded data, allowing the
 * SDK to assume that uploaded data has been synced down again.
 *
 * There are two ways to send checkpoint requests: A [Legacy] (but default and stable) format
 * supported by all PowerSync service versions, and a newer [Requests] method which is only
 * available from PowerSync service version 1.24.0 or later.
 *
 * Note that the requests checkpoint mode is an alpha API.
 */
public sealed class CheckpointMode {
    /**
     * Uses a legacy but stable endpoint to request checkpoints.
     */
    public object Legacy : CheckpointMode()

    /**
     * Adopts a new and more efficient checkpoint protocol with better support for switching users
     * on devices.
     */
    @ExperimentalCheckpointRequestsApi
    public data class Requests(
        /**
         * The periodic interval before re-posting the latest checkpoint request to the service if
         * it has not been applied in time.
         */
        val retryDelay: Duration = defaultRetryDelay,
    ) : CheckpointMode() {
        init {
            require(retryDelay >= minimumRetryDelay) {
                "The retry delay for checkpoints must be at least $minimumRetryDelay"
            }
        }

        private companion object {
            val minimumRetryDelay: Duration = 10.seconds
            val defaultRetryDelay = minimumRetryDelay
        }
    }
}
