package com.powersync.sync

import co.touchlab.kermit.Logger
import co.touchlab.stately.concurrency.AtomicBoolean
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
import io.ktor.client.engine.HttpClientEngine
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    httpEngine: HttpClientEngine? = null,
) {
    private var isUploadingCrud = AtomicBoolean(false)

    /**
     * The current sync status. This instance is updated as changes occur
     */
    var status = SyncStatus()

    private var clientId: String? = null

    private val httpClient: HttpClient

    init {
        fun HttpClientConfig<*>.configureClient() {
            install(HttpTimeout)
            install(ContentNegotiation)
        }

        httpClient =
            if (httpEngine == null) {
                HttpClient {
                    configureClient()
                }
            } else {
                HttpClient(httpEngine) {
                    configureClient()
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
            status.update(connecting = true)
            try {
                if (invalidCredentials) {
                    // This may error. In that case it will be retried again on the next
                    // iteration.
                    connector.invalidateCredentials()
                    invalidCredentials = false
                }
                streamingSyncIteration()
//                val state = streamingSyncIteration()
//                TODO: We currently always retry
//                if (!state.retry) {
//                    break;
//                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }

                logger.e("Error in streamingSync: ${e.message}")
                status.update(
                    downloadError = e,
                )
            } finally {
                status.update(
                    connected = false,
                    connecting = true,
                    downloading = false,
                )
                delay(retryDelayMs)
            }
        }
    }

    suspend fun triggerCrudUpload() {
        if (!status.connected || isUploadingCrud.value) {
            return
        }
        isUploadingCrud.value = true
        uploadAllCrud()
        isUploadingCrud.value = false
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
                    status.update(uploading = true)
                    uploadCrud()
                } else {
                    // Uploading is completed
                    bucketStorage.updateLocalTarget { getWriteCheckpoint() }
                    break
                }
            } catch (e: Exception) {
                logger.e { "Error uploading crud: ${e.message}" }
                status.update(uploading = false, uploadError = e)
                delay(retryDelayMs)
                break
            }
        }
        status.update(uploading = false)
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

                status.update(connected = true, connecting = false)
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
                retry = false,
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
        }

        status.update(downloading = false)

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
        status.update(downloading = true)

        val bucketsToDelete = state.bucketSet!!.toMutableList()
        val newBuckets = mutableSetOf<String>()

        checkpoint.checksums.forEach { checksum ->
            run {
                newBuckets.add(checksum.bucket)
                bucketsToDelete.remove(checksum.bucket)
            }
        }

        if (bucketsToDelete.size > 0) {
            logger.i { "Removing buckets [${bucketsToDelete.joinToString(separator = ", ")}]" }
        }

        state.bucketSet = newBuckets
        bucketStorage.removeBuckets(bucketsToDelete)
        bucketStorage.setTargetCheckpoint(checkpoint)

        return state
    }

    private suspend fun handleStreamingSyncCheckpointComplete(state: SyncStreamState): SyncStreamState {
        val result = bucketStorage.syncLocalDatabase(state.targetCheckpoint!!)
        if (!result.checkpointValid) {
            // This means checksums failed. Start again with a new checkpoint.
            // TODO: better back-off
            delay(50)
            state.retry = true
            // TODO handle retries
            return state
        } else if (!result.ready) {
            // Checksums valid, but need more data for a consistent checkpoint.
            // Continue waiting.
            // landing here the whole time
        } else {
            state.appliedCheckpoint = state.targetCheckpoint!!.clone()
            logger.i { "validated checkpoint ${state.appliedCheckpoint}" }
        }

        state.validatedCheckpoint = state.targetCheckpoint
        status.update(
            lastSyncedAt = Clock.System.now(),
            downloading = false,
            hasSynced = true,
            clearDownloadError = true,
        )

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
            state.retry = true
            // TODO handle retries
            return state
        } else if (!result.ready) {
            // Checksums valid, but need more data for a consistent checkpoint.
            // Continue waiting.
        } else {
            logger.i { "validated partial checkpoint ${state.appliedCheckpoint} up to priority of $priority" }
        }

        status.update(
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

        status.update(downloading = true)

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

        state.bucketSet = newBuckets.keys.toMutableSet()

        val bucketsToDelete = checkpointDiff.removedBuckets
        if (bucketsToDelete.isNotEmpty()) {
            logger.d { "Remove buckets $bucketsToDelete" }
        }
        bucketStorage.removeBuckets(bucketsToDelete)
        bucketStorage.setTargetCheckpoint(state.targetCheckpoint!!)

        return state
    }

    private suspend fun handleStreamingSyncData(
        data: SyncLine.SyncDataBucket,
        state: SyncStreamState,
    ): SyncStreamState {
        status.update(downloading = true)
        bucketStorage.saveSyncData(SyncDataBatch(listOf(data)))
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
            state.retry = true
            return state
        }
        triggerCrudUpload()
        return state
    }
}

internal data class SyncStreamState(
    var targetCheckpoint: Checkpoint?,
    var validatedCheckpoint: Checkpoint?,
    var appliedCheckpoint: Checkpoint?,
    var bucketSet: MutableSet<String>?,
    var retry: Boolean,
)
