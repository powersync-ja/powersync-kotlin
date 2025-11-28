package com.powersync.http


import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.ResponseAdapterAttributeKey
import io.ktor.client.utils.buildHeaders
import io.ktor.client.utils.dropCompressionHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.cinterop.ExperimentalForeignApi
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
import kotlinx.io.IOException
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.CoroutineContext

/**
 * A custom `NSURLSession`-based HTTP client for Apple platforms.
 *
 * This is quite similar to the default `ktor-client-darwin`, except that this one isn't affected by
 * https://youtrack.jetbrains.com/issue/KTOR-9110/Darwin-Quick-memory-growth-when-streaming-response-body.
 */
internal object AppleHttpClientEngineFactory: HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        return AppleHttpClient(HttpClientEngineConfig().apply(block))
    }
}

private class AppleHttpClient(
    override val config: HttpClientEngineConfig
) : HttpClientEngineBase("powersync-apple") {
    override val supportedCapabilities = setOf(HttpTimeoutCapability)

    private val delegate = SessionDelegate()

    private val session = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.defaultSessionConfiguration().also {
            it.setHTTPCookieStorage(null)
        },
        delegate,
        null
    )

    init {
        coroutineContext.job.invokeOnCompletion {
            // Necessary to avoid our SessionDelegate leaking.
            session.finishTasksAndInvalidate()
        }
    }

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val context = callContext()
        val nativeRequest = data.toNSUrlRequest()

        val task = session.dataTaskWithRequest(nativeRequest)
        context.job.invokeOnCompletion { cause ->
            if (cause != null) {
                task.cancel()
            }
        }

        task.resume()

        val activeRequest = delegate.registerTask(task, context, data)
        return activeRequest.response.await()
    }
}

private class SessionDelegate(): NSObject(), NSURLSessionDataDelegateProtocol {
    private val taskHandlers: ConcurrentMap<NSURLSessionTask, ActiveRequest> =
        ConcurrentMap(32)

    fun registerTask(task: NSURLSessionDataTask, context: CoroutineContext, request: HttpRequestData): ActiveRequest {
        val request = ActiveRequest(task, context, request)
        taskHandlers[task] = request
        return request
    }

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        val task = taskHandlers[dataTask]
        if (task != null) {
            task.didReceiveData(didReceiveData)
        } else {
            dataTask.cancel()
        }
    }

    override fun URLSession(session: NSURLSession, taskIsWaitingForConnectivity: NSURLSessionTask) {
    }

    @OptIn(UnsafeNumber::class)
    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (NSURLSessionResponseDisposition) -> Unit
    ) {
        val task = taskHandlers[dataTask]
        if (task != null) {
            task.didReceiveResponseHeaders()
            completionHandler(NSURLSessionResponseAllow)
        } else {
            completionHandler(NSURLSessionResponseCancel)
        }
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        taskHandlers.remove(task)?.handleDone(didCompleteWithError)
    }

    /**
     * Disable embedded redirects.
     */
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit
    ) {
        completionHandler(null)
    }
}

private class ActiveRequest(
    private val task: NSURLSessionDataTask,
    private val callContext: CoroutineContext,
    private val requestData: HttpRequestData
) {
    private val requestTime = GMTDate()
    private val bodyChunks = Channel<ByteArray>(1)

    val response: CompletableDeferred<HttpResponseData> = CompletableDeferred()

    fun didReceiveResponseHeaders() {
        val httpResponse = task.response as NSHTTPURLResponse
        response.complete(httpResponse.toResponseData(requestData))
    }

    @OptIn(UnsafeNumber::class)
    fun didReceiveData(data: NSData) {
        val kotlinBytes = ByteArray(data.length.toInt()).also {
            if (it.isNotEmpty()) {
                it.usePinned { pinned ->
                    memcpy(pinned.addressOf(0), data.bytes, data.length)
                }
            }
        }

        // Fast path: We have a reader waiting for this, don't delay the task.
        val result = bodyChunks.trySend(kotlinBytes)
        if (result.isSuccess) {
            return
        }

        if (result.isClosed) {
            task.cancel()
            return
        }

        // We need to suspend the download task until a downstream reader is available.
        CoroutineScope(callContext).launch {
            task.suspend()

            try {
                bodyChunks.send(kotlinBytes)
            } catch (e: Exception) {
                task.cancel()
                throw e
            }

            task.resume()
        }
    }

    fun handleDone(error: NSError?) {
        if (error != null) {
            val exception = handleNSError(requestData, error)
            bodyChunks.close(exception)
            if (!response.isCompleted) {
                response.completeExceptionally(exception)
            }
        } else {
            bodyChunks.close()
            if (!response.isCompleted) {
                didReceiveResponseHeaders()
            }
        }
    }

    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class, InternalAPI::class)
    fun NSHTTPURLResponse.toResponseData(requestData: HttpRequestData): HttpResponseData {
        val status = HttpStatusCode.fromValue(statusCode.convert())
        val headers = buildHeaders {
            allHeaderFields.forEach { (key, value) ->
                append(key as String, value as String)
            }

            // The NSUrlSession will decompress data transparently, so we remove compression headers to avoid ktor
            // attempting to decompress again.
            dropCompressionHeaders(requestData.method, requestData.attributes)
        }

        val body = CoroutineScope(callContext).writer {
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

public class DarwinHttpRequestException internal constructor(origin: NSError) : IOException("Exception in http request: $origin")
