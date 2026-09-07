package com.powersync.sync

import com.powersync.ExperimentalCheckpointRequestsApi
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.utils.JsonParam
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import kotlinx.serialization.json.JsonObject
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
public data class SyncOptions(
    /**
     * Enables the new client implementation written in Rust.
     *
     * This is enabled by default, and can no longer be disabled (the old Kotlin client has been
     * removed from the PowerSync SDK).
     */
    public val newClientImplementation: Boolean = true,
    /**
     * The time between attempted CRUD uploads, defaults to 1 second.
     */
    public val crudThrottle: Duration = DEFAULT_CRUD_THROTTLE_MS.milliseconds,
    /**
     * The time to wait after a download or upload error before trying again, defaults to 5 seconds.
     */
    public val retryDelay: Duration = DEFAULT_RETRY_DELAY_MS.milliseconds,
    /**
     * Additional unauthenticated parameters that can be [referenced in sync streams](https://docs.powersync.com/sync/streams/parameters#connection-parameters).
     */
    public val params: Map<String, JsonParam?> = emptyMap(),
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
    /**
     * Additional application-specific metadata that will be displayed in PowerSync service logs.
     */
    public val appMetadata: Map<String, String> = emptyMap(),
) {
    public companion object {
        /**
         * The default sync options, which are safe and stable to use.
         */
        @Deprecated("Customizing sync options is no longer necessary, use constructor instead", replaceWith = ReplaceWith("SyncOptions()"))
        public val defaults: SyncOptions = SyncOptions()

        internal const val DEFAULT_CRUD_THROTTLE_MS: Long = 1000L
        internal const val DEFAULT_RETRY_DELAY_MS: Long = 5000L
    }

    init {
        check(newClientImplementation) {
            "Support for newClientImplementation = false has been removed"
        }
    }

    /**
     * Applies legacy option parameters to this sync options instance.
     */
    internal fun merge(
        crudThrottleMs: Long,
        retryDelayMs: Long,
        params: Map<String, JsonParam?>,
        appMetadata: Map<String, String>,
    ): SyncOptions {
        if (params.isEmpty() && appMetadata.isEmpty() && crudThrottleMs == DEFAULT_CRUD_THROTTLE_MS &&
            retryDelayMs == DEFAULT_RETRY_DELAY_MS
        ) {
            return this
        }

        return copy(
            crudThrottle = crudThrottleMs.milliseconds,
            retryDelay = retryDelayMs.milliseconds,
            params =
                buildMap {
                    putAll(this@SyncOptions.params)
                    putAll(params)
                },
            appMetadata =
                buildMap {
                    putAll(this@SyncOptions.appMetadata)
                    putAll(appMetadata)
                },
        )
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
