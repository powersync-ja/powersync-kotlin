package com.powersync.http

import com.powersync.SwiftRequestLoggerConfig
import com.powersync.enableSwiftLogs
import com.powersync.sync.SyncClientConfiguration
import com.powersync.sync.configureSyncHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.ResponseAdapterAttributeKey
import io.ktor.client.utils.buildHeaders
import io.ktor.client.utils.dropCompressionHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLRequest
import platform.Foundation.getBytes
import platform.darwin.NSUInteger

/**
 * An HTTP client implemented in Swift.
 *
 * We use a Swift implementation to be able to mock HTTP requests in Swift and to work around the ktor client for Darwin
 * platforms not supporting backpressure (https://youtrack.jetbrains.com/issue/KTOR-9110/Darwin-Quick-memory-growth-when-streaming-response-body).
 */
public interface SwiftHttpClientAdapter {
    public fun request(request: NSURLRequest, listener: HttpRequestListener): InFlightHttpRequest
}

public interface HttpRequestListener {
    /**
     * Notifies Kotlin that response headers have been received.
     */
    public fun handleResponseInfo(response: NSHTTPURLResponse)

    /**
     * Notifies Kotlin that a chunk of response data is available on an HTTP stream.
     *
     * Returns true if the consumer requests the response stream to be paused. In that case, the `resume` callback will
     * be invoked once the consumer is ready again.
     */
    public fun handleResponseData(data: NSData, resume: () -> Unit): Boolean

    /**
     * Notifies Kotlin that the HTTP request has completed (regularly or because of an error).
     */
    public fun handleCompletion(error: NSError?)
}

public interface InFlightHttpRequest {
    /**
     * Closes the HTTP request and cleans up underlying resources.
     *
     * This will also be called if the request has already been completed.
     */
    public fun close()
}

private class SwiftHttpClient(
    private val adapter: SwiftHttpClientAdapter,
    override val config: HttpClientEngineConfig = HttpClientEngineConfig(),
) : HttpClientEngineBase("powersync-swift") {
    override val supportedCapabilities = setOf(HttpTimeoutCapability)

    @OptIn(UnsafeNumber::class)
    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val context = callContext()
        val requestData = data

        val nativeRequest = requestData.toNSUrlRequest()
        val pendingResponse = CompletableDeferred<HttpResponseData>()
        val bodyChunks = Channel<ByteArray>(1)
        val requestTime = GMTDate()

        var adaptedRequest: InFlightHttpRequest? = null
        adaptedRequest = adapter.request(nativeRequest, object : HttpRequestListener {
            override fun handleResponseInfo(response: NSHTTPURLResponse) {
                val status = HttpStatusCode.fromValue(response.statusCode.convert())
                val headers = buildHeaders {
                    response.allHeaderFields.forEach { (key, value) ->
                        append(key as String, value as String)
                    }

                    // The NSUrlSession will decompress data transparently, so we remove compression headers to avoid
                    // ktor attempting to decompress again.
                    dropCompressionHeaders(requestData.method, requestData.attributes)
                }

                val body = CoroutineScope(context).writer {
                    try {
                        bodyChunks.consumeEach { chunk ->
                            channel.writeFully(chunk)
                            channel.flush()
                        }
                    } catch (e: CancellationException) {
                        bodyChunks.cancel(e)
                        throw e
                    }
                }.channel

                val responseBody: Any = requestData.attributes.getOrNull(ResponseAdapterAttributeKey)
                    ?.adapt(requestData, status, headers, body, requestData.body, context)
                    ?: body
                pendingResponse.complete(
                    HttpResponseData(
                        status,
                        requestTime,
                        headers,
                        HttpProtocolVersion.HTTP_1_1,
                        responseBody,
                        context
                    )
                )
            }

            override fun handleResponseData(data: NSData, resume: () -> Unit): Boolean {
                val kotlinBytes = ByteArray(data.length.toInt()).also {
                    if (it.isNotEmpty()) {
                        it.usePinned { pinned ->
                            data.getBytes(pinned.addressOf(0), it.size.convert<NSUInteger>())
                        }
                    }
                }

                val result = bodyChunks.trySend(kotlinBytes)
                if (result.isSuccess) {
                    // No need to pause, data was handled from Kotlin
                    return false
                } else if (result.isClosed) {
                    adaptedRequest!!.close()
                    return false
                }

                // Suspend the download task until a downstream reader is available.
                CoroutineScope(context).launch {
                    try {
                        bodyChunks.send(kotlinBytes)
                    } catch (e: Exception) {
                        adaptedRequest!!.close()
                        throw e
                    }

                    resume()
                }

                return true
            }

            override fun handleCompletion(error: NSError?) {
                if (error != null) {
                    val exception = handleNSError(requestData, error)
                    bodyChunks.close(exception)
                    if (!pendingResponse.isCompleted) {
                        pendingResponse.completeExceptionally(exception)
                    }
                } else {
                    bodyChunks.close()
                    check(pendingResponse.isCompleted) { "Non-error completion without response?!" }
                }
            }
        })

        context.job.invokeOnCompletion { cause ->
            if (cause != null) {
                adaptedRequest.close()
            }
        }

        return pendingResponse.await()
    }
}

public fun swiftHttpClient(
    inner: SwiftHttpClientAdapter,
    logging: SwiftRequestLoggerConfig?
): SyncClientConfiguration =
    SyncClientConfiguration.ExistingClient(
        HttpClient(SwiftHttpClient(inner)) {
            configureSyncHttpClient()
            enableSwiftLogs(logging)
        }
    )
