package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.utils.JsonUtil
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import io.rsocket.kotlin.core.RSocketConnector
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import io.rsocket.kotlin.payload.metadata
import io.rsocket.kotlin.transport.RSocketClientTarget
import io.rsocket.kotlin.transport.RSocketConnection
import io.rsocket.kotlin.transport.RSocketTransportApi
import io.rsocket.kotlin.transport.ktor.websocket.internal.KtorWebSocketConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.CoroutineContext

/**
 * Connects to the RSocket endpoint for receiving sync lines.
 *
 * Note that we reconstruct the transport layer for RSocket by opening a WebSocket connection
 * manually instead of using the high-level RSocket Ktor integration.
 * The reason is that every request to the sync service needs its own metadata and data payload
 * (e.g. to transmit the token), but the Ktor integration only supports setting a single payload for
 * the entire client.
 */
@OptIn(RSocketTransportApi::class, ExperimentalPowerSyncAPI::class)
internal fun HttpClient.rSocketSyncStream(
    userAgent: String,
    options: ConnectionMethod.WebSocket,
    req: JsonObject,
    credentials: PowerSyncCredentials,
): Flow<ByteArray> =
    flow {
        val flowContext = currentCoroutineContext()

        val websocketUri =
            URLBuilder(credentials.endpointUri("sync/stream")).apply {
                protocol =
                    when (protocolOrNull) {
                        URLProtocol.HTTP -> URLProtocol.WS
                        else -> URLProtocol.WSS
                    }
            }

        // Note: We're using a custom connector here because we need to set options for each request
        // without creating a new HTTP client each time. The recommended approach would be to add an
        // RSocket extension to the HTTP client, but that only allows us to set the SETUP metadata for
        // all connections (bad because we need a short-lived token in there).
        // https://github.com/rsocket/rsocket-kotlin/issues/311
        val target =
            object : RSocketClientTarget {
                @RSocketTransportApi
                override suspend fun connectClient(): RSocketConnection {
                    val ws =
                        webSocketSession {
                            url.takeFrom(websocketUri)
                        }
                    return KtorWebSocketConnection(ws)
                }

                override val coroutineContext: CoroutineContext
                    get() = flowContext
            }

        val connector =
            RSocketConnector {
                connectionConfig {
                    payloadMimeType =
                        PayloadMimeType(
                            metadata = "application/json",
                            data = "application/json",
                        )

                    setupPayload {
                        buildPayload {
                            data("{}")
                            metadata(
                                JsonUtil.json.encodeToString(
                                    ConnectionSetupMetadata(
                                        token = "Bearer ${credentials.token}",
                                        userAgent = userAgent,
                                    ),
                                ),
                            )
                        }
                    }

                    keepAlive = options.keepAlive.toRSocket()
                }
            }

        val rSocket = connector.connect(target)
        val syncStream =
            rSocket.requestStream(
                buildPayload {
                    data(JsonUtil.json.encodeToString(req))
                    metadata(JsonUtil.json.encodeToString(RequestStreamMetadata("/sync/stream")))
                },
            )

        emitAll(syncStream.map { it.data.readByteArray() }.flowOn(Dispatchers.IO))
    }

/**
 * The metadata payload we need to use when connecting with RSocket.
 *
 * This corresponds to `RSocketContextMeta` on the sync service.
 */
@Serializable
private class ConnectionSetupMetadata(
    val token: String,
    @SerialName("user_agent")
    val userAgent: String,
)

/**
 * The metadata payload we send for the `REQUEST_STREAM` frame.
 */
@Serializable
private class RequestStreamMetadata(
    val path: String,
)
