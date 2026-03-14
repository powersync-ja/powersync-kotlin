package com.powersync.sync

import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.bucket.PowerSyncControlArguments
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.utils.JsonUtil
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import io.rsocket.kotlin.core.RSocketConnector
import io.rsocket.kotlin.keepalive.KeepAlive
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import io.rsocket.kotlin.payload.metadata
import io.rsocket.kotlin.transport.RSocketClientTarget
import io.rsocket.kotlin.transport.RSocketConnection
import io.rsocket.kotlin.transport.RSocketTransportApi
import io.rsocket.kotlin.transport.ktor.websocket.internal.KtorWebSocketConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

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
    req: JsonElement,
    credentials: PowerSyncCredentials,
): Flow<PowerSyncControlArguments> =
    flow {
        try {
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

                        keepAlive = KeepAlive(interval = 20.0.seconds, maxLifetime = 30.0.seconds)
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

            // Emit ConnectionEstablished only when the first frame arrives from the server, mirroring
            // the HTTP path which emits it only after receiving a 200 OK. This prevents falsely
            // reporting a successful connection when the server rejects the token: the WebSocket
            // upgrade (HTTP 101) succeeds at the transport layer, but token validation happens when
            // the server processes the stream request and responds.
            var connectionEstablishedEmitted = false
            emitAll(
                syncStream
                    .transform { payload ->
                        if (!connectionEstablishedEmitted) {
                            connectionEstablishedEmitted = true
                            emit(PowerSyncControlArguments.ConnectionEstablished)
                        }
                        emit(PowerSyncControlArguments.BinaryLine(payload.data.readByteArray()))
                    }.flowOn(Dispatchers.IO),
            )
            emit(PowerSyncControlArguments.ResponseStreamEnd)
        } catch (e: CancellationException) {
            // A CancellationException here means the transport layer failed without the server
            // sending an RSocket ERROR frame first (e.g. iOS OS detecting a dead socket, airplane
            // mode, wifi dropout). rsocket-kotlin cancels its internal connection scope with the
            // underlying IOException as the cause, which arrives here as a CancellationException
            // rather than an RSocketError. The iOS OS surfaces dead sockets promptly, so this
            // path is reached well before any keep-alive timeout fires.
            //
            // If our own collector coroutine is being cancelled (e.g. disconnect() was called),
            // ensureActive() re-throws and the cancellation propagates normally.
            // If only the RSocket-internal scope was cancelled, ensureActive() returns normally
            // and we wrap as RuntimeException so the enclosing launch{} is treated as *failed*
            // rather than *cancelled* — preventing a fetchLinesJob stall.
            currentCoroutineContext().ensureActive()
            // e.cause is the underlying error (e.g. IOException, RSocketError.ConnectionError)
            // that rsocket-kotlin wrapped in a CancellationException via connection.cancel().
            // Include it in the message so it surfaces in the streamingSync() error log.
            val rootCause = e.cause
            val message = "RSocket sync stream was interrupted: ${rootCause?.message ?: e.message}"
            if (rootCause?.message?.contains("PSYNC_S21") == true) {
                throw RSocketCredentialsExpiredException(message, e)
            }
            throw RuntimeException(message, e)
        }
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

/**
 * Thrown from [rSocketSyncStream] when the server closes the RSocket connection with a
 * PowerSync authorization error (PSYNC_S21xx) embedded in the transport-level error message.
 * Caught by [StreamingSync] to trigger credential invalidation.
 */
internal class RSocketCredentialsExpiredException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
