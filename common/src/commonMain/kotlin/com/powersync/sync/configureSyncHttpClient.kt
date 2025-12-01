package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.internal.isKnownToNotSupportBackpressure
import com.powersync.sync.StreamingSyncClient.Companion.SOCKET_TIMEOUT
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.headers
import io.ktor.util.AttributeKey
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * This API is experimental and may change in future releases.
 *
 * Configures a [HttpClient] for PowerSync sync operations.
 * Configures required plugins and default request headers.
 *
 * This is currently only necessary when using a [SyncClientConfiguration.ExistingClient] for PowerSync
 * network requests.
 *
 * Example usage:
 *
 * ```kotlin
 * val client = HttpClient() {
 *  configureSyncHttpClient()
 *  // Your own config here
 * }
 * ```
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@ExperimentalPowerSyncAPI
public fun HttpClientConfig<*>.configureSyncHttpClient(userAgent: String = userAgent()) {
    val config = this@configureSyncHttpClient
    var supportsBackpressure = true

    config.engine {
        if (isKnownToNotSupportBackpressure) {
            supportsBackpressure = false
        }
    }

    install(HttpTimeout) {
        socketTimeoutMillis = SOCKET_TIMEOUT
    }
    install(ContentNegotiation)
    if (!supportsBackpressure) {
        install(DoesNotSupportBackpressureMarker)

        install(WebSockets) {
            // RSocket Frames (Header + Payload) MUST be limited to 16,777,215 bytes, regardless of whether the utilized
            // transport protocol requires the Frame Length field: https://github.com/rsocket/rsocket/blob/master/Protocol.md#max-frame-size
            maxFrameSize = 16_777_215 + 1024 // + some extra, you never know
        }
    }

    install(DefaultRequest) {
        headers {
            append("User-Agent", userAgent)
        }
    }
}

internal object DoesNotSupportBackpressureMarker: HttpClientPlugin<Unit, DoesNotSupportBackpressureMarker> {
    override val key: AttributeKey<DoesNotSupportBackpressureMarker> = AttributeKey("DoesNotSupportBackpressureMarker")

    override fun prepare(block: Unit.() -> Unit): DoesNotSupportBackpressureMarker {
        return DoesNotSupportBackpressureMarker
    }

    override fun install(
        plugin: DoesNotSupportBackpressureMarker,
        scope: HttpClient
    ) {
        // Nothing to do, marker plugin.
    }
}
