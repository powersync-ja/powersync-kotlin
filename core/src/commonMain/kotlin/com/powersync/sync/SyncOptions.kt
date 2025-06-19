package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import io.rsocket.kotlin.keepalive.KeepAlive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    internal fun toRSocket(): KeepAlive {
        return KeepAlive(interval, maxLifetime)
    }

    internal companion object {
        val default = RSocketKeepAlive(
            interval = 20.0.seconds,
            maxLifetime = 30.0.seconds,
        )
    }
}
