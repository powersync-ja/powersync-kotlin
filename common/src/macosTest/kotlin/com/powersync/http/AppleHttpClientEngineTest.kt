package com.powersync.http

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.port
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.LineEndingMode
import io.ktor.utils.io.close
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.readUTF8LineTo
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.concurrent.AtomicInt
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class AppleHttpClientEngineTest {
    private data class HttpRequest(
        val headers: List<String>,
        @Suppress("ArrayInDataClass") val body: ByteArray?,
        val out: ByteWriteChannel,
    )

    /**
     * Spawns a simple HTTP server, returning its port.
     *
     * We use raw sockets instead of a ktor HTTP server to be able to test errors better.
     */
    @OptIn(InternalAPI::class)
    private suspend fun CoroutineScope.spawnSimpleHttpServer(
        handleResponse: suspend (HttpRequest) -> Unit
    ): Pair<Int, Job> {
        val selectorManager = SelectorManager()
        val serverSocket = aSocket(selectorManager).tcp().bind("localhost", 0)
        val port = serverSocket.port

        val job = launch {
            while (true) {
                val client = serverSocket.accept()
                val receiveChannel = client.openReadChannel()
                val sendChannel = client.openWriteChannel()

                launch {
                    var contentLength: Int? = null

                    val requestHeaders = buildList {
                        while (true) {
                            val buffer = StringBuilder()
                            receiveChannel.readUTF8LineTo(buffer, lineEnding = LineEndingMode.CRLF)
                            if (buffer.isEmpty()) break

                            println(buffer.toString())
                            add(buffer.toString())

                            val contentLengthPrefix = "Content-Length: "
                            if (buffer.startsWith(contentLengthPrefix)) {
                                contentLength = buffer.substring(contentLengthPrefix.length).toInt()
                            }
                        }
                    }

                    val body = contentLength?.let { receiveChannel.readByteArray(contentLength) }
                    handleResponse(HttpRequest(requestHeaders, body, sendChannel))
                }
            }
        }

        return port to job
    }

    suspend fun ByteWriteChannel.writeResponseHeaders(headers: List<String>) {
        headers.forEach {
            writeStringUtf8("$it\r\n")
        }

        writeStringUtf8("\r\n")
        flush()
    }

    suspend fun ByteWriteChannel.sendChunk(data: ByteArray) {
        writeStringUtf8("${data.size.toString(16)}\r\n")
        writeByteArray(data)
        writeStringUtf8("\r\n")
        flush()
    }

    private val client = HttpClient(AppleHttpClientEngineFactory)

    @Test
    fun testNoContent() = runTest {
        val (port, server) = spawnSimpleHttpServer { request ->
            request.headers[0] shouldBe "GET /testing HTTP/1.1"
            request.out.writeResponseHeaders(listOf("HTTP/1.1 204 No Content", "Server: test"))
        }

        val response = client.get("http://localhost:$port/testing")
        response.status shouldBe HttpStatusCode.NoContent
        response.headers["Server"] shouldBe "test"
        response.bodyAsText() shouldBe ""

        server.cancel()
    }

    @Test
    fun testSimpleBody() = runTest {
        val (port, server) = spawnSimpleHttpServer { request ->
            request.out.writeResponseHeaders(listOf("HTTP/1.1 200 OK", "Server: test", "Content-Length: 12"))
            request.out.writeStringUtf8("Hello world!")
            request.out.close(null)
        }

        val response = client.get("http://localhost:$port/testing")
        response.status shouldBe HttpStatusCode.OK
        response.bodyAsText() shouldBe "Hello world!"
        server.cancel()
    }

    @Test
    fun testRequestBody() = runTest {
        val (port, server) = spawnSimpleHttpServer { request ->
            request.headers[0] shouldBe "POST /testing HTTP/1.1"
            request.out.writeResponseHeaders(listOf("HTTP/1.1 204 No Content"))

            request.body?.decodeToString() shouldBe "data from client"
        }

        val response = client.post("http://localhost:$port/testing") {
            setBody("data from client")
        }
        response.status shouldBe HttpStatusCode.NoContent

        server.cancel()
    }

    @Test
    fun testErrorCloseBeforeSendingHeaders() = runTest {
        val (port, server) = spawnSimpleHttpServer { request ->
            request.out.close(null)
        }

        val exception = shouldThrow<DarwinHttpRequestException> {
            client.get("http://localhost:$port/testing")
        }

        exception.toString() shouldContain "Code=-1005"
        server.cancel()
    }

    @Test
    fun chunkedTransferEncoding() = runTest {
        val didReceiveFirstLine = CompletableDeferred<Unit>()

        val (port, server) = spawnSimpleHttpServer { request ->
            request.out.writeResponseHeaders(listOf("HTTP/1.1 200 OK", "Content-Type: application/x-ndjson", "Transfer-Encoding: chunked"))

            request.out.sendChunk("First line\n".toByteArray())
            didReceiveFirstLine.await()
            request.out.sendChunk("Second line\n".toByteArray())
            request.out.sendChunk(ByteArray(0))
        }

        client.prepareGet("http://localhost:$port/testing").execute { response ->
            response.status shouldBe HttpStatusCode.OK

            val channel = response.bodyAsChannel()
            channel.readUTF8Line() shouldBe "First line"
            didReceiveFirstLine.complete(Unit)

            channel.readUTF8Line() shouldBe "Second line"
        }

        server.cancel()
    }

    @Test
    fun chunkedTransferEncodingBackpressure() = runTest {
        val bytesWritten = AtomicInt(0)

        val (port, server) = spawnSimpleHttpServer { request ->
            request.out.writeResponseHeaders(listOf("HTTP/1.1 200 OK", "Content-Type: application/x-ndjson", "Transfer-Encoding: chunked"))

            try {
                val chunkSize = 1024 * 1024

                while (true) {
                    request.out.sendChunk(ByteArray(chunkSize))
                    bytesWritten.addAndGet(chunkSize)
                }
            } catch (_: ClosedByteChannelException) {
                // expected
            }
        }

        client.prepareGet("http://localhost:$port/testing").execute { response ->
            val channel = response.bodyAsChannel()

            // Read a few bytes.
            channel.readBuffer(1024)

            // Pause the reader.
            withContext(Dispatchers.Default) {
                delay(2.seconds)
            }

            // Backpressure and TCP flow control should have prevented the server from writing exessive
            // amounts of data in this time.
            bytesWritten.value shouldBeLessThan 1024 * 1024 * 6
            channel.cancel(null)
        }

        server.cancel()
    }

    @Test
    fun chunkedTransferEncodingCancelResponse() = runTest {
        val didCompleteChannel = CompletableDeferred<Unit>()
        val (port, server) = spawnSimpleHttpServer { request ->
            request.out.writeResponseHeaders(listOf("HTTP/1.1 200 OK", "Content-Type: application/x-ndjson", "Transfer-Encoding: chunked"))

            try {
                while (true) {
                    request.out.sendChunk("A line\n".toByteArray())
                }
            } catch (_: ClosedByteChannelException) {
                // expected
            }

            didCompleteChannel.complete(Unit)
        }

        client.prepareGet("http://localhost:$port/testing").execute { response ->
            response.status shouldBe HttpStatusCode.OK

            val channel = response.bodyAsChannel()
            channel.readUTF8Line() shouldBe "A line"

            channel.cancel(null)
            // Exit block, which should cancel the request
        }

        didCompleteChannel.await()
        server.cancel()
    }
}
