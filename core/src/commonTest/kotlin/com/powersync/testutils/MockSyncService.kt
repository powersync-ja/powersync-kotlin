package com.powersync.testutils

import app.cash.turbine.ReceiveTurbine
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.sync.LegacySyncImplementation
import com.powersync.sync.SyncLine
import com.powersync.sync.SyncStatusData
import com.powersync.utils.JsonUtil
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
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
import kotlinx.serialization.json.JsonElement

/**
 * A mock HTTP engine providing sync lines read from a coroutines [ReceiveChannel].
 *
 * Note that we can't trivially use ktor's `MockEngine` here because that engine requires a non-suspending handler
 * function which makes it very hard to cancel the channel when the sync client closes the request stream. That is
 * precisely what we may want to test though.
 */
@OptIn(LegacySyncImplementation::class)
internal class MockSyncService(
    private val lines: ReceiveChannel<Any>,
    private val syncLinesContentType: () -> ContentType,
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

        return if (data.url.encodedPath == "/sync/stream") {
            trackSyncRequest(data)
            val job =
                scope.writer {
                    lines.consume {
                        while (true) {
                            // Wait for a downstream listener being ready before requesting a sync line
                            channel.awaitFreeSpace()
                            val line = receive()
                            when (line) {
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
                                else -> throw UnsupportedOperationException("Unknown sync line type")
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
        } else if (data.url.encodedPath == "/write-checkpoint2.json") {
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

suspend inline fun ReceiveTurbine<SyncStatusData>.waitFor(matcher: (SyncStatusData) -> Boolean) {
    while (true) {
        val item = awaitItem()
        if (matcher(item)) {
            break
        }

        item.anyError?.let {
            error("Unexpected error in $item")
        }
    }
}
