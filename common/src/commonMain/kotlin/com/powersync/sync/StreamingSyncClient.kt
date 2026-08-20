package com.powersync.sync

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.stately.concurrency.AtomicReference
import com.powersync.ExperimentalCheckpointRequestsApi
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import com.powersync.bucket.BucketStorage
import com.powersync.bucket.CheckpointRequestPayload
import com.powersync.bucket.CheckpointRequestResponse
import com.powersync.bucket.PowerSyncControlArguments
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.connectors.CustomCheckpointRequestConnector
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.SubscriptionGroup
import com.powersync.db.crud.CrudEntry
import com.powersync.db.schema.Schema
import com.powersync.utils.JsonUtil
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.EOFException
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalPowerSyncAPI::class, ExperimentalCheckpointRequestsApi::class)
internal class StreamingSyncClient(
    private val status: SyncStatus,
    private val bucketStorage: BucketStorage,
    private val connector: PowerSyncBackendConnector,
    private val uploadCrud: suspend () -> Unit,
    private val retryDelay: Duration = 5.seconds,
    private val crudUploadThrottle: Duration = 1.seconds,
    private val logger: Logger,
    private val params: JsonObject,
    private val options: SyncOptions,
    private val schema: Schema,
    private val activeSubscriptions: StateFlow<List<SubscriptionGroup>>,
    private val appMetadata: Map<String, String> = emptyMap(),
) {
    private val requestedCrudUploads = Channel<Unit>(CONFLATED)
    private val completedCrudUploads = Channel<Unit>(CONFLATED)
    private val checkpointSignals = CheckpointStateSignals()

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

    private suspend fun loadClientId(): String {
        clientId?.let { return it }

        val id = bucketStorage.getClientId()
        clientId = id
        return id
    }

    /**
     * Triggers a crud upload without awaiting it.
     *
     * A crud upload will be started at some point after this call, but the upload actor throttles
     * these requests.
     */
    suspend fun triggerCrudUpload() {
        requestedCrudUploads.send(Unit)
    }

    suspend fun streamingSync() {
        try {
            coroutineScope {
                launch { downloadLoop() }
                launch { crudUploadLoop() }
                launch { repostUnacknowledgedCheckpointRequests() }
            }
        } finally {
            checkpointSignals.disconnected()
        }
    }

    private suspend fun downloadLoop() {
        var invalidCredentials = false
        loadClientId()

        while (true) {
            var result = SyncIterationResult()

            try {
                if (invalidCredentials) {
                    // This may error. In that case it will be retried again on the next
                    // iteration.
                    connector.invalidateCredentials()
                    invalidCredentials = false
                }
                result = ActiveIteration().syncIteration()
            } catch (e: PowerSyncRSocketError) {
                // RSocketError extends Throwable directly (not Exception), so it needs its own
                // catch block to avoid accidentally catching JVM Errors (OutOfMemoryError, etc.).
                if (e.indicatesInvalidCredentials()) {
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
                    // Wait for the delay, or another component wanting to request a checkpoint.
                    withTimeoutOrNull(retryDelay) {
                        checkpointSignals.waitForCheckpointWaiter()
                        logger.v { "Resuming due to pendin checkpoint waiter" }
                    }
                }
            }
        }
    }

    private suspend fun crudUploadLoop() {
        while (true) {
            coroutineScope {
                // To throttle, ensure we spend at least this much time in the iteration.
                launch { delay(crudUploadThrottle) }

                try {
                    uploadAllCrud()
                } finally {
                    logger.v { "crud upload: notify completion" }
                    completedCrudUploads.send(Unit)
                }
            }

            requestedCrudUploads.receive()
        }
    }

    private suspend fun uploadAllCrud() {
        var checkedCrudItem: CrudEntry? = null

        while (true) {
            /*
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
                    bucketStorage.updateLocalTarget {
                        when (options.checkpointMode) {
                            CheckpointMode.Legacy -> getLegacyWriteCheckpoint()
                            is CheckpointMode.Requests -> requestNextCheckpointFromService()
                        }
                    }
                    break
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    status.update { copy(uploading = false) }
                    throw e
                }

                status.update { copy(uploading = false, uploadError = e) }
                logger.e { "Error uploading crud: ${e.message}" }
                delay(retryDelay)
                break
            }
        }
        status.update { copy(uploading = false) }
    }

    private suspend fun requestNextCheckpointFromService(): Long {
        checkpointSignals.waitForCheckpointRequestsReady()

        val nextCheckpointRequestId = bucketStorage.readOrUpdateCheckpoint("next")!!
        return requestCheckpointFromService(
            CheckpointRequestPayload(
                clientId = loadClientId(),
                checkpointRequestId = nextCheckpointRequestId,
            ),
        )
    }

    private suspend fun requestCheckpointFromService(payload: CheckpointRequestPayload): Long {
        // First, check if we can use a custom checkpoint request implementation.
        (connector as? CustomCheckpointRequestConnector)?.let {
            return it.postCheckpointRequest(payload.clientId, payload.checkpointRequestId)
        }

        val credentials = connector.getCredentialsCached()
        require(credentials != null) { "Not logged in" }
        val uri = credentials.endpointUri("sync/checkpoint-request")

        val response =
            httpClient.post(uri) {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Authorization, "Token ${credentials.token}")
                }

                setBody(JsonUtil.json.encodeToString(payload))
            }

        if (response.status.value == 401) {
            connector.invalidateCredentials()
        }
        if (response.status.value == 404) {
            throw CheckpointRequestException.InstanceNotSupported()
        }
        if (response.status.value != 200) {
            throw CheckpointRequestException("Error getting checkpoint request: ${response.status}")
        }

        val body = JsonUtil.json.decodeFromString<CheckpointRequestResponse>(response.body())
        return body.data.id
    }

    private suspend fun getLegacyWriteCheckpoint(): Long {
        val credentials = connector.getCredentialsCached()
        require(credentials != null) { "Not logged in" }
        val uri = credentials.endpointUri("write-checkpoint2.json?client_id=${loadClientId()}")

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

    private suspend fun repostUnacknowledgedCheckpointRequests() {
        val retryDelay =
            when (val mode = options.checkpointMode) {
                CheckpointMode.Legacy -> return
                is CheckpointMode.Requests -> mode.retryDelay
            }

        while (true) {
            try {
                checkpointSignals.waitForCheckpointRequestsReady(wakeDownloadLoop = false)

                val requestId = bucketStorage.readOrUpdateCheckpoint("current") ?: continue
                // Give the request some time to sync.
                delay(retryDelay)

                // If a new request was made, reset the timer.
                if (requestId != bucketStorage.readOrUpdateCheckpoint("current")) {
                    continue
                }

                // If the request was applied, we don't need to retry.
                if (status.isCheckpointRequestApplied(requestId)) {
                    continue
                }

                // Make sure we're online and ready before making the request
                checkpointSignals.waitForCheckpointRequestsReady(wakeDownloadLoop = false)

                // It's safe if this request races with a new one. The service will reject it.
                logger.d { "Retry checkpoint request $requestId" }
                requestCheckpointFromService(
                    CheckpointRequestPayload(
                        clientId = loadClientId(),
                        checkpointRequestId = requestId,
                    ),
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }

                logger.w(throwable = e) { "Error retrying checkpoint request." }
                delay(retryDelay)
            }
        }
    }

    private suspend fun seedCheckpointRequestState(request: CheckpointRequestPayload) {
        val seed = requestCheckpointFromService(request)
        bucketStorage.readOrUpdateCheckpoint("seed", seed)
    }

    /**
     * Implementation of a sync iteration that delegates to helper functions implemented in the
     * Rust core extension.
     *
     * This avoids us having to decode sync lines in Kotlin, unlocking the RSocket protocol and
     * improving performance.
     */
    private inner class ActiveIteration {
        private val needsCredentialsRefresh = Channel<Unit>(CONFLATED)

        suspend fun syncIteration(): SyncIterationResult {
            return try {
                val (establishConnection, subscriptions) = start() ?: return SyncIterationResult()

                coroutineScope {
                    val channel = produceEvents(establishConnection, subscriptions)

                    channel.consumeEach { line ->
                        val instructions = bucketStorage.control(line)
                        for (instruction in instructions) {
                            when (instruction) {
                                is Instruction.CloseSyncStream -> {
                                    val hideDisconnect = instruction.hideDisconnect
                                    logger.v { "Closing sync stream connection. Hide disconnect: $hideDisconnect" }
                                    return@coroutineScope SyncIterationResult(hideDisconnect)
                                }

                                is Instruction.EstablishSyncStream -> {
                                    error("Already has stream")
                                }

                                is Instruction.NonInterruptingInstruction -> {
                                    handleInstruction(instruction)
                                }
                            }
                        }
                    }

                    SyncIterationResult()
                }
            } finally {
                checkpointSignals.downloadIterationEnded()

                // This can't be canceled because we need to send a stop message, which is async, to
                // clean up resources.
                withContext(NonCancellable) {
                    stop()
                }

                logger.v { "Sync stream connection shut down" }
            }
        }

        /**
         * Passes current subscriptions and other options to the core extension to begin a sync
         * iteration.
         *
         * Returns the start instruction and  used subscriptions (to compare them against later
         * changes).
         */
        private suspend fun start(): Pair<Instruction.EstablishSyncStream, List<SubscriptionGroup>>? {
            val subscriptions = activeSubscriptions.value

            val startInstructions =
                bucketStorage.control(
                    PowerSyncControlArguments.Start(
                        parameters = params,
                        schema = schema,
                        includeDefaults = options.includeDefaultStreams,
                        activeStreams = subscriptions.map { it.key },
                        appMetadata = appMetadata,
                        checkpointMode =
                            when (options.checkpointMode) {
                                CheckpointMode.Legacy -> "legacy"
                                is CheckpointMode.Requests -> "requests"
                            },
                    ),
                )
            var start: Instruction.EstablishSyncStream? = null

            for (instruction in startInstructions) {
                when (instruction) {
                    is Instruction.EstablishSyncStream -> {
                        start = instruction
                    }

                    is Instruction.CloseSyncStream -> {
                        return null
                    }

                    is Instruction.NonInterruptingInstruction -> {
                        handleInstruction(instruction)
                    }
                }
            }

            return start?.let { it to subscriptions }
        }

        /**
         * Sends a stop command and uses returned instructions to e.g. clean up the sync status.
         */
        private suspend fun stop() {
            val instructions = bucketStorage.control(PowerSyncControlArguments.Stop)
            // We don't need to handle interrupting instructions since we're unconditionally
            // ending the sync iteration at this point.
            for (instruction in instructions) {
                if (instruction is Instruction.NonInterruptingInstruction) {
                    handleInstruction(instruction)
                }
            }

            if (instructions.isEmpty()) {
                // For errors reported by the core extension in powersync_control, the sync client
                // is reset, and we don't get an updated sync status. Fall back to the offline sync
                // status in that case.
                val offlineStatus = bucketStorage.resolveOfflineSyncStatus()
                status.update { copy(core = offlineStatus) }
            }
        }

        /**
         * Produces events the sync client needs to react to.
         *
         * Decoded sync lines received from the PowerSync service are the main source for events.
         * Additionally, this watches local events (changed subscriptions, completed uploads) to
         * forward those to the stream client.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private fun CoroutineScope.produceEvents(
            instruction: Instruction.EstablishSyncStream,
            initialSubscriptions: List<SubscriptionGroup>,
        ): ReceiveChannel<PowerSyncControlArguments> {
            var currentSubscriptions = initialSubscriptions

            return produce(CoroutineName("produceEvents")) {
                launch(CoroutineName("Receive lines from PowerSync service")) {
                    receiveTextOrBinaryLines(instruction.request).collect {
                        send(it)
                    }
                }

                launch(CoroutineName("Watch subscription updates")) {
                    activeSubscriptions.collect { newSubscriptions ->
                        if (currentSubscriptions !== newSubscriptions) {
                            currentSubscriptions = newSubscriptions
                            send(
                                PowerSyncControlArguments.UpdateSubscriptions(newSubscriptions.map { it.key }),
                            )
                        }
                    }
                }

                launch(CoroutineName("Track completed CRUD uploads")) {
                    while (true) {
                        completedCrudUploads.receive()
                        send(PowerSyncControlArguments.CompletedUpload)
                    }
                }

                launch(CoroutineName("Prefetch credentials")) {
                    needsCredentialsRefresh.consumeEach {
                        try {
                            connector.updateCredentials()

                            logger.v { "Stopping because new credentials are available" }
                            // Token has been refreshed, start another iteration
                            send(PowerSyncControlArguments.DidRefreshToken)
                        } catch (e: Exception) {
                            if (e is CancellationException) {
                                throw e
                            } else {
                                logger.w(throwable = e) { "Failure in updateCredentials" }
                            }
                        }
                    }
                }

                instruction.checkpointRequest?.let { seedRequest ->
                    launch(CoroutineName("Seed checkpoint state")) {
                        // Start checkpoint request validation concurrently, without blocking
                        // line processing on it. We need to do this at the start of every sync
                        // iteration to ensure service and clients align on checkpoint ids, even
                        // if the active user has changed between iterations.
                        try {
                            checkpointSignals.markCheckpointsReady {
                                seedCheckpointRequestState(seedRequest)
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e

                            send(PowerSyncControlArguments.CheckpointSeedFailed(e))
                        }
                    }
                }
            }
        }

        private suspend fun handleInstruction(instruction: Instruction.NonInterruptingInstruction) {
            when (instruction) {
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
                    status.update { copy(core = instruction.status) }
                }

                is Instruction.FetchCredentials -> {
                    if (instruction.didExpire) {
                        connector.invalidateCredentials()
                    } else {
                        // Token expires soon - refresh it in the background
                        needsCredentialsRefresh.send(Unit)
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

private class SyncIterationResult(
    val hideDisconnectStateAndReconnectImmediately: Boolean = false,
)
