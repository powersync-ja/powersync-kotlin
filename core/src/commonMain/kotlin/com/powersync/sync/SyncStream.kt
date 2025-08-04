package com.powersync.sync

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.stately.concurrency.AtomicReference
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncException
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketRequest
import com.powersync.bucket.BucketStorage
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.PowerSyncControlArguments
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.crud.CrudEntry
import com.powersync.db.schema.Schema
import com.powersync.db.schema.toSerializable
import com.powersync.sync.SyncStream.Companion.SOCKET_TIMEOUT
import com.powersync.utils.JsonUtil
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
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
import io.ktor.utils.io.readUTF8Line
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock

/**
 * Configures a [HttpClient] for PowerSync sync operations.
 * Sets up required plugins and default request headers.
 * This API is experimental and may change in future releases.
 *
 * Example usage:
 *
 * val client = HttpClient() {
 *  configureSyncHttpClient()
 *  // Your own config here
 * }
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
@ExperimentalPowerSyncAPI
public fun HttpClientConfig<*>.configureSyncHttpClient(userAgent: String = userAgent()) {
    install(HttpTimeout) {
        socketTimeoutMillis = SOCKET_TIMEOUT
    }
    install(ContentNegotiation)
    install(WebSockets)

    install(DefaultRequest) {
        headers {
            append("User-Agent", userAgent)
        }
    }
}

@OptIn(ExperimentalPowerSyncAPI::class)
internal class SyncStream(
    private val bucketStorage: BucketStorage,
    private val connector: PowerSyncBackendConnector,
    private val uploadCrud: suspend () -> Unit,
    private val retryDelayMs: Long = 5000L,
    private val logger: Logger,
    private val params: JsonObject,
    private val uploadScope: CoroutineScope,
    private val options: SyncOptions,
    private val schema: Schema,
    createClient: (HttpClientConfig<*>.() -> Unit) -> HttpClient,
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
            is SyncClientConfiguration.ExtendedConfig ->
                createClient(options.userAgent, config.block)

            is SyncClientConfiguration.ExistingClient -> config.client

            null -> createClient(options.userAgent)
        }

    private fun createClient(
        userAgent: String,
        additionalConfig: HttpClientConfig<*>.() -> Unit = {},
    ) = HttpClient {
        configureSyncHttpClient(userAgent)
        additionalConfig()
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

    private suspend fun <T> connectToSyncEndpoint(
        req: JsonElement,
        supportBson: Boolean,
        block: suspend (isBson: Boolean, response: HttpResponse) -> T,
    ): T {
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

        return request.execute { httpResponse ->
            val isBson = httpResponse.contentType() == bsonStream

            if (httpResponse.status.value == 401) {
                connector.invalidateCredentials()
            }

            if (httpResponse.status != HttpStatusCode.OK) {
                throw RuntimeException("Received error when connecting to sync stream: ${httpResponse.bodyAsText()}")
            }

            status.update { copy(connected = true, connecting = false) }
            block(isBson, httpResponse)
        }
    }

    private fun receiveTextLines(req: JsonElement): Flow<String> =
        flow {
            connectToSyncEndpoint(req, supportBson = false) { isBson, response ->
                check(!isBson)

                emitAll(response.body<ByteReadChannel>().lines())
            }
        }

    private fun receiveTextOrBinaryLines(req: JsonElement): Flow<PowerSyncControlArguments> =
        flow {
            connectToSyncEndpoint(req, supportBson = false) { isBson, response ->
                val body = response.body<ByteReadChannel>()

                if (isBson) {
                    emitAll(body.bsonObjects().map { PowerSyncControlArguments.BinaryLine(it) })
                } else {
                    emitAll(body.lines().map { PowerSyncControlArguments.TextLine(it) })
                }
            }
        }

    private suspend fun streamingSyncIteration() {
        coroutineScope {
            if (options.newClientImplementation) {
                val iteration = ActiveIteration(this)

                try {
                    iteration.start()
                } finally {
                    // This can't be cancelled because we need to send a stop message, which is async, to
                    // clean up resources.
                    withContext(NonCancellable) {
                        iteration.stop()
                    }
                }
            } else {
                legacySyncIteration()
            }
        }
    }

    @OptIn(LegacySyncImplementation::class)
    private suspend fun CoroutineScope.legacySyncIteration() {
        LegacyIteration(this).streamingSyncIteration()
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
        var fetchLinesJob: Job? = null,
        var credentialsInvalidation: Job? = null,
    ) {
        // Using a channel for control invocations so that they're handled by a single coroutine,
        // avoiding races between concurrent jobs like fetching credentials.
        private val controlInvocations = Channel<PowerSyncControlArguments>()

        private suspend fun invokeControl(args: PowerSyncControlArguments) {
            val instructions = bucketStorage.control(args)
            instructions.forEach { handleInstruction(it) }
        }

        suspend fun start() {
            invokeControl(
                PowerSyncControlArguments.Start(
                    parameters = params,
                    schema = schema.toSerializable(),
                ),
            )

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

                Instruction.CloseSyncStream -> {
                    logger.v { "Closing sync stream connection" }
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

    @LegacySyncImplementation
    private inner class LegacyIteration(
        val scope: CoroutineScope,
    ) {
        suspend fun streamingSyncIteration() {
            check(schema.rawTables.isEmpty()) {
                "Raw tables are only supported by the Rust sync client."
            }

            val bucketEntries = bucketStorage.getBucketStates()
            val initialBuckets = mutableMapOf<String, String>()

            var state =
                SyncStreamState(
                    targetCheckpoint = null,
                    validatedCheckpoint = null,
                    appliedCheckpoint = null,
                    bucketSet = initialBuckets.keys.toMutableSet(),
                )

            bucketEntries.forEach { entry ->
                initialBuckets[entry.bucket] = entry.opId
            }

            val req =
                StreamingSyncRequest(
                    buckets =
                        initialBuckets.map { (bucket, after) ->
                            BucketRequest(
                                bucket,
                                after,
                            )
                        },
                    clientId = clientId!!,
                    parameters = params,
                )

            lateinit var receiveLines: Job
            receiveLines =
                scope.launch {
                    receiveTextLines(JsonUtil.json.encodeToJsonElement(req)).collect { value ->
                        val line = JsonUtil.json.decodeFromString<SyncLine>(value)

                        state = handleInstruction(line, value, state)

                        if (state.abortIteration) {
                            receiveLines.cancel()
                        }
                    }
                }

            receiveLines.join()
            status.update { abortedDownload() }
        }

        private suspend fun handleInstruction(
            line: SyncLine,
            jsonString: String,
            state: SyncStreamState,
        ): SyncStreamState =
            when (line) {
                is SyncLine.FullCheckpoint -> handleStreamingSyncCheckpoint(line, state)
                is SyncLine.CheckpointDiff -> handleStreamingSyncCheckpointDiff(line, state)
                is SyncLine.CheckpointComplete -> handleStreamingSyncCheckpointComplete(state)
                is SyncLine.CheckpointPartiallyComplete ->
                    handleStreamingSyncCheckpointPartiallyComplete(
                        line,
                        state,
                    )

                is SyncLine.KeepAlive -> handleStreamingKeepAlive(line, state)
                is SyncLine.SyncDataBucket -> handleStreamingSyncData(line, state)
                SyncLine.UnknownSyncLine -> {
                    logger.w { "Unhandled instruction $jsonString" }
                    state
                }
            }

        private suspend fun handleStreamingSyncCheckpoint(
            line: SyncLine.FullCheckpoint,
            state: SyncStreamState,
        ): SyncStreamState {
            val (checkpoint) = line
            state.targetCheckpoint = checkpoint

            val bucketsToDelete = state.bucketSet!!.toMutableList()
            val newBuckets = mutableSetOf<String>()

            checkpoint.checksums.forEach { checksum ->
                run {
                    newBuckets.add(checksum.bucket)
                    bucketsToDelete.remove(checksum.bucket)
                }
            }

            state.bucketSet = newBuckets
            startTrackingCheckpoint(checkpoint, bucketsToDelete)

            return state
        }

        private suspend fun startTrackingCheckpoint(
            checkpoint: Checkpoint,
            bucketsToDelete: List<String>,
        ) {
            val progress = bucketStorage.getBucketOperationProgress()
            status.update {
                copy(
                    downloading = true,
                    downloadProgress = SyncDownloadProgress(progress, checkpoint),
                )
            }

            if (bucketsToDelete.isNotEmpty()) {
                logger.i { "Removing buckets [${bucketsToDelete.joinToString(separator = ", ")}]" }
            }

            bucketStorage.removeBuckets(bucketsToDelete)
            bucketStorage.setTargetCheckpoint(checkpoint)
        }

        private suspend fun handleStreamingSyncCheckpointComplete(state: SyncStreamState): SyncStreamState {
            val checkpoint = state.targetCheckpoint!!
            var result = bucketStorage.syncLocalDatabase(checkpoint)
            val pending = isUploadingCrud.get()

            if (!result.checkpointValid) {
                // This means checksums failed. Start again with a new checkpoint.
                // TODO: better back-off
                delay(50)
                state.abortIteration = true
                // TODO handle retries
                return state
            } else if (!result.ready && pending != null) {
                // We have pending entries in the local upload queue or are waiting to confirm a write checkpoint, which
                // prevented this checkpoint from applying. Wait for that to complete and try again.
                logger.d { "Could not apply checkpoint due to local data. Waiting for in-progress upload before retrying." }
                pending.done.await()

                result = bucketStorage.syncLocalDatabase(checkpoint)
            }

            if (result.checkpointValid && result.ready) {
                state.appliedCheckpoint = checkpoint.clone()
                logger.i { "validated checkpoint ${state.appliedCheckpoint}" }

                state.validatedCheckpoint = state.targetCheckpoint
                status.update { copyWithCompletedDownload() }
            } else {
                logger.d { "Could not apply checkpoint. Waiting for next sync complete line" }
            }

            return state
        }

        private suspend fun handleStreamingSyncCheckpointPartiallyComplete(
            line: SyncLine.CheckpointPartiallyComplete,
            state: SyncStreamState,
        ): SyncStreamState {
            val priority = line.priority
            val result = bucketStorage.syncLocalDatabase(state.targetCheckpoint!!, priority)
            if (!result.checkpointValid) {
                // This means checksums failed. Start again with a new checkpoint.
                // TODO: better back-off
                delay(50)
                state.abortIteration = true
                // TODO handle retries
                return state
            } else if (!result.ready) {
                // Checkpoint is valid, but we have local data preventing this to be published. We'll try to resolve this
                // once we have a complete checkpoint if the problem persists.
            } else {
                logger.i { "validated partial checkpoint ${state.appliedCheckpoint} up to priority of $priority" }
            }

            status.update {
                copy(
                    priorityStatusEntries =
                        buildList {
                            // All states with a higher priority can be deleted since this partial sync includes them.
                            addAll(status.priorityStatusEntries.filter { it.priority >= line.priority })
                            add(
                                PriorityStatusEntry(
                                    priority = priority,
                                    lastSyncedAt = Clock.System.now(),
                                    hasSynced = true,
                                ),
                            )
                        },
                )
            }
            return state
        }

        private suspend fun handleStreamingSyncCheckpointDiff(
            checkpointDiff: SyncLine.CheckpointDiff,
            state: SyncStreamState,
        ): SyncStreamState {
            // TODO: It may be faster to just keep track of the diff, instead of the entire checkpoint
            if (state.targetCheckpoint == null) {
                throw Exception("Checkpoint diff without previous checkpoint")
            }

            val newBuckets = mutableMapOf<String, BucketChecksum>()

            state.targetCheckpoint!!.checksums.forEach { checksum ->
                newBuckets[checksum.bucket] = checksum
            }
            checkpointDiff.updatedBuckets.forEach { checksum ->
                newBuckets[checksum.bucket] = checksum
            }

            checkpointDiff.removedBuckets.forEach { bucket -> newBuckets.remove(bucket) }

            val newCheckpoint =
                Checkpoint(
                    lastOpId = checkpointDiff.lastOpId,
                    checksums = newBuckets.values.toList(),
                    writeCheckpoint = checkpointDiff.writeCheckpoint,
                )

            state.targetCheckpoint = newCheckpoint
            startTrackingCheckpoint(newCheckpoint, checkpointDiff.removedBuckets)

            return state
        }

        private suspend fun handleStreamingSyncData(
            data: SyncLine.SyncDataBucket,
            state: SyncStreamState,
        ): SyncStreamState {
            val batch = SyncDataBatch(listOf(data))
            bucketStorage.saveSyncData(batch)
            status.update {
                copy(
                    downloading = true,
                    downloadProgress = downloadProgress?.incrementDownloaded(batch),
                )
            }
            return state
        }

        private fun handleStreamingKeepAlive(
            keepAlive: SyncLine.KeepAlive,
            state: SyncStreamState,
        ): SyncStreamState {
            val (tokenExpiresIn) = keepAlive

            if (tokenExpiresIn <= 0) {
                // Connection would be closed automatically right after this
                logger.i { "Token expiring reconnect" }
                connector.invalidateCredentials()
                state.abortIteration = true
                return state
            }
            // Don't await the upload job, we can keep receiving sync lines
            triggerCrudUploadAsync()
            return state
        }
    }

    internal companion object {
        // The sync service sends a token keepalive message roughly every 20 seconds. So if we don't receive a message
        // in twice that time, assume the connection is broken.
        private const val SOCKET_TIMEOUT: Long = 40_000

        private val ndjson = ContentType("application", "x-ndjson")
        private val bsonStream = ContentType("application", "vnd.powersync.bson-stream")

        fun defaultHttpClient(config: HttpClientConfig<*>.() -> Unit) =
            HttpClient {
                config(this)
            }

        fun ByteReadChannel.lines(): Flow<String> =
            flow {
                while (!isClosedForRead) {
                    val line = readUTF8Line()
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
                            throw PowerSyncException(
                                "Unexpected end of response in middle of BSON sync line",
                                null,
                            )
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

@LegacySyncImplementation
internal data class SyncStreamState(
    var targetCheckpoint: Checkpoint?,
    var validatedCheckpoint: Checkpoint?,
    var appliedCheckpoint: Checkpoint?,
    var bucketSet: MutableSet<String>?,
    var abortIteration: Boolean = false,
)

private class PendingCrudUpload(
    val done: CompletableDeferred<Unit>,
)
