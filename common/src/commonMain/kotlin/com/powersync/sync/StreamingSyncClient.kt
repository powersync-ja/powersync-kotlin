package com.powersync.sync

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.stately.concurrency.AtomicReference
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import com.powersync.bucket.BucketStorage
import com.powersync.bucket.PowerSyncControlArguments
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.SubscriptionGroup
import com.powersync.db.crud.CrudEntry
import com.powersync.db.schema.Schema
import com.powersync.db.schema.toSerializable
import com.powersync.utils.JsonUtil
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.readLineStrict
import io.rsocket.kotlin.RSocketError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.EOFException
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalPowerSyncAPI::class)
internal class StreamingSyncClient(
    private val bucketStorage: BucketStorage,
    private val connector: PowerSyncBackendConnector,
    private val uploadCrud: suspend () -> Unit,
    private val retryDelayMs: Long = 5000L,
    private val logger: Logger,
    private val params: JsonObject,
    private val uploadScope: CoroutineScope,
    private val options: SyncOptions,
    private val schema: Schema,
    private val activeSubscriptions: StateFlow<List<SubscriptionGroup>>,
    private val appMetadata: Map<String, String> = emptyMap(),
) {
    private var isUploadingCrud = AtomicReference<PendingCrudUpload?>(null)
    private var completedCrudUploads = Channel<Unit>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * The current sync status. This instance is mutated as changes occur
     */
    val status = SyncStatus()

    private var clientId: String? = null

    private val httpClient: HttpClient =
        when (val config = options.clientConfiguration) {
            is SyncClientConfiguration.ExtendedConfig -> {
                HttpClient {
                    configureSyncHttpClient(options.userAgent)
                    config.block(this)
                }
            }

            is SyncClientConfiguration.ExistingClient -> {
                config.client
            }
        }

    fun invalidateCredentials() {
        connector.invalidateCredentials()
    }

    suspend fun streamingSync() {
        var invalidCredentials = false
        clientId = bucketStorage.getClientId()
        var result = SyncIterationResult()

        while (true) {
            if (!result.hideDisconnectStateAndReconnectImmediately) {
                status.update { copy(connecting = true) }
            }
            result = SyncIterationResult()

            try {
                if (invalidCredentials) {
                    // This may error. In that case it will be retried again on the next
                    // iteration.
                    connector.invalidateCredentials()
                    invalidCredentials = false
                }
                result = streamingSyncIteration()
            } catch (e: RSocketError) {
                // RSocketError extends Throwable directly (not Exception), so it needs its own
                // catch block to avoid accidentally catching JVM Errors (OutOfMemoryError, etc.).
                if (e is RSocketError.Setup.Rejected) {
                    // The server rejected the RSocket SETUP frame, most likely due to an invalid
                    // token. Invalidate credentials so a fresh token is fetched on the next attempt.
                    connector.invalidateCredentials()
                }
                logger.e("Error in streamingSync: ${e.message}")
                status.update { copy(downloadError = e) }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }

                if (e is RSocketCredentialsExpiredException) {
                    // Auth error (PSYNC_S21xx) delivered via the RSocket transport-layer failure
                    // path. Invalidate credentials so a fresh token is fetched on the next attempt.
                    connector.invalidateCredentials()
                }

                logger.e("Error in streamingSync: ${e.message}")
                status.update { copy(downloadError = e) }
            } finally {
                if (!result.hideDisconnectStateAndReconnectImmediately) {
                    status.update {
                        copy(
                            connected = false,
                            connecting = true,
                            downloading = false,
                        )
                    }
                    delay(retryDelayMs)
                }
            }
        }
    }

    fun triggerCrudUploadAsync(): Job =
        uploadScope.launch(CoroutineName("triggerCrudUploadAsync")) {
            val thisIteration = PendingCrudUpload(CompletableDeferred())
            var holdingUploadLock = false

            try {
                if (!status.connected || !isUploadingCrud.compareAndSet(null, thisIteration)) {
                    return@launch
                }

                holdingUploadLock = true
                uploadAllCrud()
            } finally {
                if (holdingUploadLock) {
                    logger.v { "crud upload: notify completion" }
                    completedCrudUploads.send(Unit)
                    isUploadingCrud.set(null)
                }

                thisIteration.done.complete(Unit)
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

    private fun <T> syncEndpointFlow(
        req: JsonElement,
        supportBson: Boolean,
        innerFlow: suspend FlowCollector<T>.(isBson: Boolean, response: HttpResponse) -> Unit,
    ): Flow<T> {
        val originalFlow =
            channelFlow<T> {
                val credentials = connector.getCredentialsCached()
                require(credentials != null) { "Not logged in" }

                val uri = credentials.endpointUri("sync/stream")

                val bodyJson = JsonUtil.json.encodeToString(req)

                val request =
                    httpClient.preparePost(uri) {
                        contentType(ContentType.Application.Json)
                        headers {
                            append(HttpHeaders.Authorization, "Token ${credentials.token}")
                            if (supportBson) {
                                accept(bsonStream.withParameter("q", "0.9"))
                                // Also indicate ndjson support as fallback
                                append(HttpHeaders.Accept, ndjson.withParameter("q", "0.8"))
                            } else {
                                accept(ndjson)
                            }
                        }
                        setBody(bodyJson)
                    }

                request.execute { httpResponse ->
                    val isBson = httpResponse.contentType() == bsonStream

                    if (httpResponse.status.value == 401) {
                        connector.invalidateCredentials()
                    }

                    if (httpResponse.status != HttpStatusCode.OK) {
                        throw RuntimeException("Received error when connecting to sync stream: ${httpResponse.bodyAsText()}")
                    }

                    // Within this context, we can create the inner flow and push items to the channel.
                    flow { innerFlow(isBson, httpResponse) }.collect {
                        send(it)
                    }
                }
            }

        // We're only using a channelFlow to allow consumer and producer to be on different coroutine contexts (which is
        // a requirement because request.execute changes the context to the one of the engine). However, we still want
        // each emit() to block until it has been received to preserve backpressure.
        return originalFlow.buffer(Channel.RENDEZVOUS)
    }

    private fun receiveTextOrBinaryLines(req: JsonElement): Flow<PowerSyncControlArguments> {
        val needsRSocket = httpClient.attributes[WebSocketIfNecessaryPlugin.needsRSocketKey]

        return if (!needsRSocket) {
            // If we can use streamed HTTP responses that respect backpressure, prefer to do that.
            syncEndpointFlow(req, supportBson = false) { isBson, response ->
                emit(PowerSyncControlArguments.ConnectionEstablished)
                val body = response.body<ByteReadChannel>()

                if (isBson) {
                    emitAll(body.bsonObjects().map { PowerSyncControlArguments.BinaryLine(it) })
                } else {
                    emitAll(body.lines().map { PowerSyncControlArguments.TextLine(it) })
                }

                emit(PowerSyncControlArguments.ResponseStreamEnd)
            }
        } else {
            // Use RSocket as a fallback to ensure we have backpressure on platforms that don't support it natively.
            flow {
                val credentials =
                    requireNotNull(connector.getCredentialsCached()) { "Not logged in" }

                emitAll(
                    httpClient.rSocketSyncStream(
                        userAgent = options.userAgent,
                        credentials = credentials,
                        req = req,
                    ),
                )
            }
        }
    }

    private suspend fun streamingSyncIteration(): SyncIterationResult =
        coroutineScope {
            val iteration = ActiveIteration(this)

            try {
                iteration.start()
            } finally {
                // This can't be canceled because we need to send a stop message, which is async, to
                // clean up resources.
                withContext(NonCancellable) {
                    iteration.stop()
                }
            }
        }

    /**
     * Implementation of a sync iteration that delegates to helper functions implemented in the
     * Rust core extension.
     *
     * This avoids us having to decode sync lines in Kotlin, unlocking the RSocket protocol and
     * improving performance.
     */
    private inner class ActiveIteration(
        val scope: CoroutineScope,
    ) {
        var fetchLinesJob: Job? = null
        var credentialsInvalidation: Job? = null

        // Using a channel for control invocations so that they're handled by a single coroutine,
        // avoiding races between concurrent jobs like fetching credentials.
        private val controlInvocations = Channel<PowerSyncControlArguments>()
        private var result = SyncIterationResult()

        private suspend fun invokeControl(args: PowerSyncControlArguments) {
            val instructions = bucketStorage.control(args)
            instructions.forEach { handleInstruction(it) }
        }

        suspend fun start(): SyncIterationResult {
            var subscriptions = activeSubscriptions.value

            invokeControl(
                PowerSyncControlArguments.Start(
                    parameters = params,
                    schema = schema.toSerializable(),
                    includeDefaults = options.includeDefaultStreams,
                    activeStreams = subscriptions.map { it.key },
                    appMetadata = appMetadata,
                ),
            )

            val listenForUpdatedSubscriptions =
                scope.launch {
                    activeSubscriptions.collect {
                        if (subscriptions !== it) {
                            subscriptions = it
                            controlInvocations.send(
                                PowerSyncControlArguments.UpdateSubscriptions(activeSubscriptions.value.map { it.key }),
                            )
                        }
                    }
                }

            var hadSyncLine = false
            for (line in controlInvocations) {
                val instructions = bucketStorage.control(line)
                instructions.forEach { handleInstruction(it) }

                if (!hadSyncLine && (line is PowerSyncControlArguments.TextLine || line is PowerSyncControlArguments.BinaryLine)) {
                    // Trigger a crud upload when receiving the first sync line: We could have
                    // pending local writes made while disconnected, so in addition to listening on
                    // updates to `ps_crud`, we also need to trigger a CRUD upload in some other
                    // cases. We do this on the first sync line because the client is likely to be
                    // online in that case.
                    hadSyncLine = true
                    triggerCrudUploadAsync()
                }
            }

            listenForUpdatedSubscriptions.cancel()
            return result
        }

        suspend fun stop() {
            invokeControl(PowerSyncControlArguments.Stop)
            fetchLinesJob?.join()
        }

        private suspend fun handleInstruction(instruction: Instruction) {
            when (instruction) {
                is Instruction.EstablishSyncStream -> {
                    fetchLinesJob?.cancelAndJoin()
                    fetchLinesJob =
                        scope
                            .launch {
                                launch {
                                    logger.v { "listening for completed uploads" }
                                    for (completion in completedCrudUploads) {
                                        controlInvocations.send(PowerSyncControlArguments.CompletedUpload)
                                    }
                                }

                                launch {
                                    connect(instruction)
                                }
                            }.also {
                                it.invokeOnCompletion {
                                    controlInvocations.close()
                                }
                            }
                }

                is Instruction.CloseSyncStream -> {
                    val hideDisconnect = instruction.hideDisconnect
                    logger.v { "Closing sync stream connection. Hide disconnect: $hideDisconnect" }
                    result = SyncIterationResult(hideDisconnect)
                    fetchLinesJob!!.cancelAndJoin()
                    fetchLinesJob = null
                    logger.v { "Sync stream connection shut down" }
                }

                Instruction.FlushSileSystem -> {
                    // We have durable file systems, so flushing is not necessary
                }

                is Instruction.LogLine -> {
                    logger.log(
                        severity =
                            when (instruction.severity) {
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

                is Instruction.FetchCredentials -> {
                    if (instruction.didExpire) {
                        connector.invalidateCredentials()
                    } else {
                        // Token expires soon - refresh it in the background
                        if (credentialsInvalidation == null) {
                            val job =
                                scope.launch {
                                    connector.updateCredentials()
                                    logger.v { "Stopping because new credentials are available" }

                                    // Token has been refreshed, start another iteration
                                    controlInvocations.send(PowerSyncControlArguments.Stop)
                                }
                            job.invokeOnCompletion {
                                credentialsInvalidation = null
                            }
                            credentialsInvalidation = job
                        }
                    }
                }

                Instruction.DidCompleteSync -> {
                    status.update { copy(downloadError = null) }
                }

                is Instruction.UnknownInstruction -> {
                    logger.w { "Unknown instruction received from core extension: ${instruction.raw}" }
                }
            }
        }

        private suspend fun connect(start: Instruction.EstablishSyncStream) {
            receiveTextOrBinaryLines(start.request).collect {
                controlInvocations.send(it)
            }
        }
    }

    internal companion object Companion {
        // The sync service sends a token keepalive message roughly every 20 seconds. So if we don't receive a message
        // in twice that time, assume the connection is broken.
        internal const val SOCKET_TIMEOUT: Long = 40_000

        private val ndjson = ContentType("application", "x-ndjson")
        private val bsonStream = ContentType("application", "vnd.powersync.bson-stream")

        fun defaultHttpClient(config: HttpClientConfig<*>.() -> Unit) =
            HttpClient {
                config(this)
            }

        fun ByteReadChannel.lines(): Flow<String> =
            flow {
                while (!isClosedForRead) {
                    val line = readLineStrict()
                    if (line != null) {
                        emit(line)
                    }
                }
            }

        fun ByteReadChannel.bsonObjects(): Flow<ByteArray> =
            flow {
                while (true) {
                    emit(readBsonObject() ?: break)
                }
            }

        private suspend fun ByteReadChannel.readBsonObject(): ByteArray? {
            if (isClosedForRead || !awaitContent(1)) {
                return null // eof at start of object
            }

            return readBuffer(4).use { buffer ->
                // 4 byte length prefix, see https://bsonspec.org/spec.html
                val length = buffer.peek().readIntLe()
                if (length < 5) {
                    // At the very least we need the 4 byte length and a zero terminator
                    throw PowerSyncException("Invalid BSON message, to small", null)
                }

                // length is the total size of the frame, including the 4 byte length header
                var remaining = length - 4

                while (remaining > 0) {
                    val bytesRead =
                        readAvailable(1) { source ->
                            val available = source.readAtMostTo(buffer, remaining.toLong())
                            available.toInt()
                        }
                    if (bytesRead == -1) {
                        // No bytes available, wait for more
                        if (isClosedForRead || !awaitContent(1)) {
                            throw EOFException()
                        }
                    } else {
                        remaining -= bytesRead
                    }
                }

                buffer.readByteArray()
            }
        }
    }
}

private class PendingCrudUpload(
    val done: CompletableDeferred<Unit>,
)

private class SyncIterationResult(
    val hideDisconnectStateAndReconnectImmediately: Boolean = false,
)
