package com.powersync.sync

import co.touchlab.kermit.Logger
import co.touchlab.stately.concurrency.AtomicReference
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketRequest
import com.powersync.bucket.BucketStorage
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.WriteCheckpointResponse
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.crud.CrudEntry
import com.powersync.utils.JsonUtil
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject

internal class SyncStream(
    private val bucketStorage: BucketStorage,
    private val connector: PowerSyncBackendConnector,
    private val uploadCrud: suspend () -> Unit,
    private val retryDelayMs: Long = 5000L,
    private val logger: Logger,
    private val params: JsonObject,
    private val scope: CoroutineScope,
    createClient: (HttpClientConfig<*>.() -> Unit) -> HttpClient,
) {
    private var isUploadingCrud = AtomicReference<PendingCrudUpload?>(null)

    /**
     * The current sync status. This instance is updated as changes occur
     */
    var status = SyncStatus()

    private var clientId: String? = null

    private val httpClient: HttpClient =
        createClient {
            install(HttpTimeout)
            install(ContentNegotiation)
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

    private fun streamingSyncRequest(req: StreamingSyncRequest): Flow<String> =
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

    private suspend fun streamingSyncIteration(): SyncStreamState {
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
                buckets = initialBuckets.map { (bucket, after) -> BucketRequest(bucket, after) },
                clientId = clientId!!,
                parameters = params,
            )

        streamingSyncRequest(req).collect { value ->
            val line = JsonUtil.json.decodeFromString<SyncLine>(value)

            state = handleInstruction(line, value, state)

            if (state.abortIteration) {
                return@collect
            }
        }

        status.update { abortedDownload() }

        return state
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
        status.update { copy(downloading = true, downloadProgress = downloadProgress?.incrementDownloaded(batch)) }
        bucketStorage.saveSyncData(batch)
        return state
    }

    private suspend fun handleStreamingKeepAlive(
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

    internal companion object {
        fun defaultHttpClient(config: HttpClientConfig<*>.() -> Unit) =
            HttpClient {
                config(this)
            }
    }
}

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
