package com.powersync.testutils

import app.cash.turbine.ReceiveTurbine
import com.powersync.bucket.CheckpointRequestPayload
import com.powersync.bucket.CheckpointRequestResponse
import com.powersync.bucket.CheckpointRequestResponseData
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.sync.SyncStatusData
import com.powersync.utils.JsonUtil
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.awaitFreeSpace
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeStringUtf8
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonElement
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.max

/**
 * A mock HTTP engine providing sync lines read from a coroutines [ReceiveChannel].
 *
 * Note that we can't trivially use ktor's `MockEngine` here because that engine requires a non-suspending handler
 * function which makes it very hard to cancel the channel when the sync client closes the request stream. That is
 * precisely what we may want to test though.
 */
internal class MockSyncService(
    private val lines: () -> ReceiveChannel<Any>,
    private val syncLinesContentType: () -> ContentType,
    private val requestCheckpoints: CheckpointRequestsTestState,
    private val generateCheckpoint: () -> WriteCheckpointResponse,
    private val trackSyncRequest: suspend (HttpRequestData) -> Unit,
) : HttpClientEngineBase("sync-service") {
    override val config: HttpClientEngineConfig
        get() = Config

    override val supportedCapabilities: Set<HttpClientEngineCapability<out Any>> =
        setOf(
            HttpTimeoutCapability,
        )

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val context = callContext()
        val scope = CoroutineScope(context)

        val path = data.url.encodedPath
        return if (path == "/sync/stream") {
            trackSyncRequest(data)
            val job =
                scope.writer {
                    lines().consume {
                        while (true) {
                            // Wait for a downstream listener being ready before requesting a sync line
                            channel.awaitFreeSpace()
                            when (val line = receive()) {
                                is SyncLine -> {
                                    val serializedLine = JsonUtil.json.encodeToString(line)
                                    channel.writeStringUtf8("$serializedLine\n")
                                }

                                is JsonElement -> {
                                    val serializedLine = JsonUtil.json.encodeToString(line)
                                    channel.writeStringUtf8("$serializedLine\n")
                                }

                                is ByteArray -> {
                                    channel.writeByteArray(line)
                                }

                                is String -> {
                                    channel.writeStringUtf8("$line\n")
                                }

                                else -> {
                                    throw UnsupportedOperationException("Unknown sync line type")
                                }
                            }

                            channel.flush()
                        }
                    }
                }

            HttpResponseData(
                HttpStatusCode.OK,
                GMTDate(),
                headersOf(HttpHeaders.ContentType, syncLinesContentType().toString()),
                HttpProtocolVersion.HTTP_1_1,
                job.channel,
                context,
            )
        } else if (path == "/sync/checkpoint-request") {
            if (!requestCheckpoints.checkpointRequestsSupported) {
                return HttpResponseData(
                    HttpStatusCode.NotFound,
                    GMTDate(),
                    headersOf(),
                    HttpProtocolVersion.HTTP_1_1,
                    body = "",
                    context,
                )
            }

            val request = JsonUtil.json.decodeFromString<CheckpointRequestPayload>(data.body.toByteArray().decodeToString())
            requestCheckpoints.lastCheckpointRequest.update { max(it, request.checkpointRequestId) }
            requestCheckpoints.checkpointRequestCount.update { it + 1 }
            requestCheckpoints.beforeCheckpointRequestResponse()

            HttpResponseData(
                HttpStatusCode.OK,
                GMTDate(),
                headersOf(HttpHeaders.ContentType, "application/json"),
                HttpProtocolVersion.HTTP_1_1,
                JsonUtil.json.encodeToString(
                    CheckpointRequestResponse(
                        data = CheckpointRequestResponseData(requestCheckpoints.lastCheckpointRequest.value),
                    ),
                ),
                context,
            )
        } else if (path == "/write-checkpoint2.json") {
            HttpResponseData(
                HttpStatusCode.OK,
                GMTDate(),
                headersOf(),
                HttpProtocolVersion.HTTP_1_1,
                JsonUtil.json.encodeToString(generateCheckpoint()),
                context,
            )
        } else {
            HttpResponseData(
                HttpStatusCode.BadRequest,
                GMTDate(),
                headersOf(),
                HttpProtocolVersion.HTTP_1_1,
                "",
                context,
            )
        }
    }

    private object Config : HttpClientEngineConfig()
}

suspend inline fun ReceiveTurbine<SyncStatusData>.waitFor(
    allowError: Boolean = false,
    matcher: (SyncStatusData) -> Boolean,
) {
    while (true) {
        val item = awaitItem()
        if (matcher(item)) {
            break
        }

        if (!allowError) {
            item.anyError?.let {
                error("Unexpected error in $item")
            }
        }
    }
}

internal class CheckpointRequestsTestState {
    val lastCheckpointRequest = MutableStateFlow(0L)
    val checkpointRequestCount = MutableStateFlow(0)
    var checkpointRequestsSupported = true
    var beforeCheckpointRequestResponse: suspend () -> Unit = {}
}
