/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal

import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import kotlin.coroutines.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import platform.Foundation.*

@OptIn(DelicateCoroutinesApi::class)
internal class DarwinTaskHandler(
        private val requestData: HttpRequestData,
        private val callContext: CoroutineContext,
) {
    val response: CompletableDeferred<HttpResponseData> = CompletableDeferred()

    private val requestTime: GMTDate = GMTDate()
    private val bodyChunks = Channel<ByteArray>(capacity = 1)

    private var pendingFailure: Throwable? = null
        get() = field?.also { field = null }

    private val body: ByteReadChannel =
            GlobalScope.writer(callContext, autoFlush = true) {
                        try {
                            bodyChunks.consumeEach {
                                channel.writeFully(it)
                            }
                        } catch (cause: CancellationException) {
                            bodyChunks.cancel(cause)
                            throw cause
                        }
                    }
                    .channel

    fun receiveData(dataTask: NSURLSessionDataTask, data: NSData) {
        if (!response.isCompleted) {
            val result = dataTask.response as NSHTTPURLResponse
            response.complete(result.toResponseData(requestData))
        }

        val bytes = data.toByteArray()
        try {
            runBlocking { bodyChunks.send(bytes) }
        } catch (_: CancellationException) {
            dataTask.cancel()
        } catch (_: ClosedSendChannelException) {
            dataTask.cancel()
        }
    }

    fun saveFailure(cause: Throwable) {
        pendingFailure = cause
    }

    fun complete(task: NSURLSessionTask, didCompleteWithError: NSError?) {
        if (didCompleteWithError != null) {
            val exception = pendingFailure ?: handleNSError(requestData, didCompleteWithError)
            bodyChunks.close(exception)
            response.completeExceptionally(exception)
            return
        }

        if (!response.isCompleted) {
            val result = task.response as NSHTTPURLResponse
            response.complete(result.toResponseData(requestData))
        }

        bodyChunks.close()
    }

    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class, InternalAPI::class)
    fun NSHTTPURLResponse.toResponseData(requestData: HttpRequestData): HttpResponseData {
        val status = HttpStatusCode.fromValue(statusCode.convert())
        val headers = readHeaders(requestData.method, requestData.attributes)
        val responseBody: Any =
                requestData
                        .attributes
                        .getOrNull(ResponseAdapterAttributeKey)
                        ?.adapt(requestData, status, headers, body, requestData.body, callContext)
                        ?: body

        return HttpResponseData(
                status,
                requestTime,
                headers,
                HttpProtocolVersion.HTTP_1_1,
                responseBody,
                callContext
        )
    }
}
