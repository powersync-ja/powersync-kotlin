package com.powersync.sync

import io.rsocket.kotlin.keepalive.KeepAlive
import kotlin.time.Duration.Companion.seconds

public class SyncOptions(
    public val method: ConnectionMethod = ConnectionMethod.WebSocket,
)

/**
 * The connection method to use when the SDK connects to the sync service.
 */
public sealed interface ConnectionMethod {
    /**
     * Receive sync lines via an streamed HTTP response from the sync service.
     *
     * This mode is less efficient than [WebSocket] because it doesn't support backpressure
     * properly and uses JSON instead of the more efficient BSON representation for sync lines.
     */
    public data object Http: ConnectionMethod

    /**
     * Receive binary sync lines via RSocket over a WebSocket connection.
     *
     * This is the default mode, and recommended for most clients.
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
