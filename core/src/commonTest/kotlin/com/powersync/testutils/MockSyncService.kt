package com.powersync.testutils

import app.cash.turbine.ReceiveTurbine
import com.powersync.sync.SyncLine
import com.powersync.sync.SyncStatusData
import com.powersync.utils.JsonUtil
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondBadRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

internal class MockSyncService private constructor(
    private val scope: CoroutineScope,
    private val lines: Flow<SyncLine>,
) {
    private fun handleRequest(
        scope: MockRequestHandleScope,
        request: HttpRequestData,
    ): HttpResponseData =
        if (request.url.encodedPath == "/sync/stream") {
            val channel = ByteChannel(autoFlush = true)
            this.scope.launch {
                lines.collect {
                    val serializedLine = JsonUtil.json.encodeToString(it)
                    channel.writeStringUtf8("$serializedLine\n")
                }
            }

            scope.respond(channel)
        } else {
            scope.respondBadRequest()
        }

    companion object {
        fun client(
            scope: CoroutineScope,
            lines: Flow<SyncLine>,
        ): HttpClientEngine {
            val service = MockSyncService(scope, lines)
            return MockEngine { request ->
                service.handleRequest(this, request)
            }
        }
    }
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
