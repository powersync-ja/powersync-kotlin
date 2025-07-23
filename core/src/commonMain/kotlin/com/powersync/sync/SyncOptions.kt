package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.rsocket.kotlin.keepalive.KeepAlive
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
     */
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
        @property:ExperimentalPowerSyncAPI
        public val method: ConnectionMethod = ConnectionMethod.Http,
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

/**
 * The connection method to use when the SDK connects to the sync service.
 */
@ExperimentalPowerSyncAPI
public sealed interface ConnectionMethod {
    /**
     * Receive sync lines via  streamed HTTP response from the sync service.
     *
     * This mode is less efficient than [WebSocket] because it doesn't support backpressure
     * properly and uses JSON instead of the more efficient BSON representation for sync lines.
     *
     * This is currently the default, but this will be changed once [WebSocket] support is stable.
     */
    @ExperimentalPowerSyncAPI
    public data object Http : ConnectionMethod

    /**
     * Receive binary sync lines via RSocket over a WebSocket connection.
     *
     * This connection mode is currently experimental and requires a recent sync service to work.
     * WebSocket support is only available when enabling the [SyncOptions.newClientImplementation].
     */
    @ExperimentalPowerSyncAPI
    public data class WebSocket(
        val keepAlive: RSocketKeepAlive = RSocketKeepAlive.default,
    ) : ConnectionMethod
}

/**
 * Keep-alive options for long-running RSocket streams:
 *
 * The client will ping the server every [interval], and assumes the connection to be closed if it
 * hasn't received an acknowledgement in [maxLifetime].
 */
@ExperimentalPowerSyncAPI
public data class RSocketKeepAlive(
    val interval: Duration,
    val maxLifetime: Duration,
) {
    internal fun toRSocket(): KeepAlive = KeepAlive(interval, maxLifetime)

    internal companion object {
        val default =
            RSocketKeepAlive(
                interval = 20.0.seconds,
                maxLifetime = 30.0.seconds,
            )
    }
}
