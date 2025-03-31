package com.powersync.testutils

import app.cash.turbine.ReceiveTurbine
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
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.writeStringUtf8
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.encodeToString

/**
 * A mock HTTP engine providing sync lines read from a coroutines [ReceiveChannel].
 *
 * Note that we can't trivially use ktor's `MockEngine` here because that engine requires a non-suspending handler
 * function which makes it very hard to cancel the channel when the sync client closes the request stream. That is
 * precisely what we may want to test though.
 */
internal class MockSyncService(
    private val lines: ReceiveChannel<SyncLine>,
) : HttpClientEngineBase("sync-service") {

    override val config: HttpClientEngineConfig
        get() = Config

    override val supportedCapabilities: Set<HttpClientEngineCapability<out Any>> = setOf(
        HttpTimeoutCapability,
    )

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val context = callContext()
        val scope = CoroutineScope(context)

        return if (data.url.encodedPath == "/sync/stream") {
            val job = scope.writer(autoFlush = true) {
                lines.consumeEach {
                    val serializedLine = JsonUtil.json.encodeToString(it)
                    channel.writeStringUtf8("$serializedLine\n")
                }
            }

            HttpResponseData(
                HttpStatusCode.OK,
                GMTDate(),
                headersOf(),
                HttpProtocolVersion.HTTP_1_1,
                job.channel,
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
