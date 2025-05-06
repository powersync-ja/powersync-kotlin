package com.powersync.sync

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.stately.concurrency.AtomicBoolean
import com.powersync.bucket.BucketStorage
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.crud.CrudEntry
import com.powersync.utils.JsonUtil
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.rsocket.kotlin.core.RSocketConnector
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.metadata
import io.rsocket.kotlin.transport.RSocketClientTarget
import io.rsocket.kotlin.transport.RSocketConnection
import io.rsocket.kotlin.transport.RSocketTransportApi
import io.rsocket.kotlin.transport.ktor.websocket.internal.KtorWebSocketConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal class SyncStream(
    private val bucketStorage: BucketStorage,
    private val connector: PowerSyncBackendConnector,
    private val uploadCrud: suspend () -> Unit,
    private val retryDelayMs: Long = 5000L,
    private val logger: Logger,
    private val params: JsonObject,
    private val scope: CoroutineScope,
    private val options: SyncOptions,
    createClient: (HttpClientConfig<*>.() -> Unit) -> HttpClient,
) {
    private var isUploadingCrud = AtomicBoolean(false)
    private val completedCrudUploads = Channel<Unit>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * The current sync status. This instance is mutated as changes occur
     */
    val status = SyncStatus()

    private var clientId: String? = null

    private val httpClient: HttpClient =
        createClient {
            install(HttpTimeout)
            install(ContentNegotiation)
            install(WebSockets)

            install(DefaultRequest) {
                headers {
                    append("User-Agent", userAgent())
                }
            }
        }

    fun invalidateCredentials() {
        connector.invalidateCredentials()
    }

    suspend fun streamingSync() {
        var invalidCredentials = false
        clientId = bucketStorage.getClientId()
        while (true) {
            status.update { copy(connecting = true) }
            try {
                if (invalidCredentials) {
                    // This may error. In that case it will be retried again on the next
                    // iteration.
                    connector.invalidateCredentials()
                    invalidCredentials = false
                }
                streamingSyncIteration()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }

                logger.e("Error in streamingSync: ${e.message}")
                status.update { copy(downloadError = e) }
            } finally {
                status.update { copy(connected = false, connecting = true, downloading = false) }
                delay(retryDelayMs)
            }
        }
    }

    fun triggerCrudUploadAsync(): Job =
        scope.launch {
            if (!status.connected || !isUploadingCrud.compareAndSet(expected = false, new = true)) {
                return@launch
            }

            try {
                uploadAllCrud()
                completedCrudUploads.send(Unit)
            } finally {
                isUploadingCrud.value = false
            }
        }

    private suspend fun uploadAllCrud() {
        var checkedCrudItem: CrudEntry? = null

        while (true) {
            /**
             * This is the first item in the FIFO CRUD queue.
             */
            try {
                val nextCrudItem = bucketStorage.nextCrudItem()
                if (nextCrudItem != null) {
                    if (nextCrudItem.clientId == checkedCrudItem?.clientId) {
                        logger.w(
                            """Potentially previously uploaded CRUD entries are still present in the upload queue.
                        Make sure to handle uploads and complete CRUD transactions or batches by calling and awaiting their [.complete()] method.
                        The next upload iteration will be delayed.""",
                        )
                        throw Exception("Delaying due to previously encountered CRUD item.")
                    }

                    checkedCrudItem = nextCrudItem
                    status.update { copy(uploading = true) }
                    uploadCrud()
                } else {
                    // Uploading is completed
                    bucketStorage.updateLocalTarget { getWriteCheckpoint() }
                    break
                }
            } catch (e: Exception) {
                status.update { copy(uploading = false, uploadError = e) }

                if (e is CancellationException) {
                    throw e
                }

                logger.e { "Error uploading crud: ${e.message}" }
                delay(retryDelayMs)
                break
            }
        }
        status.update { copy(uploading = false) }
    }

    private suspend fun getWriteCheckpoint(): String {
        val credentials = connector.getCredentialsCached()
        require(credentials != null) { "Not logged in" }
        val uri = credentials.endpointUri("write-checkpoint2.json?client_id=$clientId")

        val response =
            httpClient.get(uri) {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Token ${credentials.token}")
                    append("User-Id", credentials.userId ?: "")
                }
            }
        if (response.status.value == 401) {
            connector.invalidateCredentials()
        }
        if (response.status.value != 200) {
            throw Exception("Error getting write checkpoint: ${response.status}")
        }

        val body = JsonUtil.json.decodeFromString<WriteCheckpointResponse>(response.body())
        return body.data.writeCheckpoint
    }

    private fun connectViaHttp(req: JsonObject): Flow<String> =
        flow {
            val credentials = connector.getCredentialsCached()
            require(credentials != null) { "Not logged in" }

            val uri = credentials.endpointUri("sync/stream")

            val bodyJson = JsonUtil.json.encodeToString(req)

            val request =
                httpClient.preparePost(uri) {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Authorization, "Token ${credentials.token}")
                        append("User-Id", credentials.userId ?: "")
                    }
                    timeout { socketTimeoutMillis = Long.MAX_VALUE }
                    setBody(bodyJson)
                }

            request.execute { httpResponse ->
                if (httpResponse.status.value == 401) {
                    connector.invalidateCredentials()
                }

                if (httpResponse.status != HttpStatusCode.OK) {
                    throw RuntimeException("Received error when connecting to sync stream: ${httpResponse.bodyAsText()}")
                }

                status.update { copy(connected = true, connecting = false) }
                val channel: ByteReadChannel = httpResponse.body()

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line()
                    if (line != null) {
                        emit(line)
                    }
                }
            }
        }

    private fun connectViaWebSocket(req: JsonObject, options: ConnectionMethod.WebSocket): Flow<ByteArray> = flow {
        val credentials = requireNotNull(connector.getCredentialsCached()) { "Not logged in" }

        emitAll(httpClient.rSocketSyncStream(
            options = options,
            req = req,
            credentials = credentials
        ))
    }

    private suspend fun streamingSyncIteration() {
        val iteration = ActiveIteration()

        try {
            iteration.start()
        } finally {
            // This can't be cancelled because we need to send a stop message, which is async, to
            // clean up resources.
            withContext(NonCancellable) {
                iteration.stop()
            }
        }
    }

    private inner class ActiveIteration(
        var fetchLinesJob: Job? = null,
    ) {
        suspend fun start() {
            control("start", JsonUtil.json.encodeToString(params))
            fetchLinesJob?.join()
        }

        suspend fun stop() {
            control("stop")
            fetchLinesJob?.join()
        }

        private suspend fun control(op: String, payload: String? = null) {
            val instructions = bucketStorage.control(op, payload)
            handleInstructions(instructions)
        }

        private suspend fun control(op: String, payload: ByteArray) {
            val instructions = bucketStorage.control(op, payload)
            handleInstructions(instructions)
        }

        private suspend fun handleInstructions(instructions: List<Instruction>) {
            instructions.forEach { handleInstruction(it) }
        }

        private suspend fun handleInstruction(instruction: Instruction) {
            when (instruction) {
                is Instruction.EstablishSyncStream -> {
                    fetchLinesJob?.cancelAndJoin()
                    fetchLinesJob = scope.launch {
                        launch {
                            for (completion in completedCrudUploads) {
                                control("completed_upload")
                            }
                        }

                        launch {
                            connect(instruction)
                        }
                    }
                }
                Instruction.CloseSyncStream -> {
                    fetchLinesJob!!.cancelAndJoin()
                    fetchLinesJob = null
                }
                Instruction.FlushSileSystem -> {
                    // We have durable file systems, so flushing is not necessary
                }
                is Instruction.LogLine -> {
                    logger.log(
                        severity = when(instruction.severity) {
                            "DEBUG" -> Severity.Debug
                            "INFO" -> Severity.Debug
                            else -> Severity.Warn
                        },
                        message = instruction.line,
                        tag = logger.tag,
                        throwable = null,
                    )
                }
                is Instruction.UpdateSyncStatus -> {
                    status.update {
                        applyCoreChanges(instruction.status)
                    }
                }
                is Instruction.FetchCredentials -> TODO()
                Instruction.DidCompleteSync -> status.update { copy(downloadError=null) }
                Instruction.UnknownInstruction -> TODO()
            }
        }

        private suspend fun connect(start: Instruction.EstablishSyncStream) {
            when (val method = options.method) {
                ConnectionMethod.Http -> connectViaHttp(start.request).collect { rawLine ->
                    control("line_text", rawLine)
                }
                is ConnectionMethod.WebSocket -> connectViaWebSocket(start.request, method).collect { binaryLine ->
                    control("line_binary", binaryLine)
                }
            }
        }
    }

    internal companion object {
        fun defaultHttpClient(config: HttpClientConfig<*>.() -> Unit) =
            HttpClient {
                config(this)
            }
    }
}
