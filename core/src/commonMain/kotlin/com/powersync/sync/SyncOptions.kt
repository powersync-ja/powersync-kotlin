package com.powersync.sync

import io.rsocket.kotlin.keepalive.KeepAlive
import kotlin.time.Duration.Companion.seconds

public class SyncOptions(
    public val method: ConnectionMethod = ConnectionMethod.Http,
)

/**
 * The connection method to use when the SDK connects to the sync service.
 */
public sealed interface ConnectionMethod {
    /**
     * Receive sync lines via  streamed HTTP response from the sync service.
     *
     * This mode is less efficient than [WebSocket] because it doesn't support backpressure
     * properly and uses JSON instead of the more efficient BSON representation for sync lines.
     *
     * This is currently the default, but this will be changed once [WebSocket] support is stable.
     */
    public data object Http: ConnectionMethod

    /**
     * Receive binary sync lines via RSocket over a WebSocket connection.
     *
     * This connection mode is currently experimental and requires a recent sync service to work.
     */
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
