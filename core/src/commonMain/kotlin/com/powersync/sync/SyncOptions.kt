package com.powersync.sync

import com.powersync.PowerSyncDatabase
import com.powersync.ExperimentalPowerSyncAPI
import io.rsocket.kotlin.keepalive.KeepAlive
import kotlin.time.Duration.Companion.seconds

/**
 * Experimental options that can be passed to [PowerSyncDatabase.connect] to specify an experimental
 * connection mechanism.
 *
 * The new connection implementation is more efficient and we expect it to become the default in
 * the future. At the moment, the implementation is not covered by the stability guarantees we offer
 * for the rest of the SDK though.
 */
public class SyncOptions @ExperimentalPowerSyncAPI constructor(
    public val newClientImplementation: Boolean = false,
    public val method: ConnectionMethod = ConnectionMethod.Http,
) {
    public companion object {
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
    public data object Http: ConnectionMethod

    /**
     * Receive binary sync lines via RSocket over a WebSocket connection.
     *
     * This connection mode is currently experimental and requires a recent sync service to work.
     * WebSocket support is only available when enabling the [SyncOptions.newClientImplementation].
     */
    @ExperimentalPowerSyncAPI
    public data class WebSocket(
        val keepAlive: KeepAlive = DefaultKeepAlive
    ): ConnectionMethod {
        private companion object {
            val DefaultKeepAlive = KeepAlive(
                interval = 20.0.seconds,
                maxLifetime = 30.0.seconds,
            )
        }
    }
}
