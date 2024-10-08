package com.powersync.sync

import co.touchlab.kermit.Logger
import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketRequest
import com.powersync.bucket.BucketStorage
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.WriteCheckpointResponse
import co.touchlab.stately.concurrency.AtomicBoolean
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.crud.CrudEntry
import com.powersync.utils.JsonUtil
import io.ktor.client.HttpClient
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
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement

internal class SyncStream(
    private val bucketStorage: BucketStorage,
    private val connector: PowerSyncBackendConnector,
    private val uploadCrud: suspend () -> Unit,
    private val retryDelayMs: Long = 5000L,
    private val logger: Logger,
    private val params: JsonObject
) {
    private var isUploadingCrud = AtomicBoolean(false)

    /**
     * The current sync status. This instance is updated as changes occur
     */
    var status = SyncStatus()

    private var clientId: String? = null

    private val httpClient: HttpClient = HttpClient {
        install(HttpTimeout)
        install(ContentNegotiation)
    }

    companion object {
        fun isStreamingSyncData(obj: JsonObject): Boolean {
            return obj.containsKey("data")
        }

        fun isStreamingKeepAlive(obj: JsonObject): Boolean {
            return obj.containsKey("token_expires_in")
        }

        fun isStreamingSyncCheckpoint(obj: JsonObject): Boolean {
            return obj.containsKey("checkpoint")
        }

        fun isStreamingSyncCheckpointComplete(obj: JsonObject): Boolean {
            return obj.containsKey("checkpoint_complete")
        }

        fun isStreamingSyncCheckpointDiff(obj: JsonObject): Boolean {
            return obj.containsKey("checkpoint_diff")
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
                //If the coroutine was cancelled, don't log an error
                if(e !is CancellationException) {
                    logger.e(e) { "Error in streamingSync" }
                }
                invalidCredentials = true
                status.update(
                    downloadError = e
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
            status.update(uploading = true)
            /**
             * This is the first item in the FIFO CRUD queue.
             */
            try {
                val nextCrudItem = bucketStorage.nextCrudItem()
                if (nextCrudItem != null) {
                    if (nextCrudItem.clientId == checkedCrudItem?.clientId) {
                        // This will force a higher log level than exceptions which are caught here.
                        logger.w(
                            """Potentially previously uploaded CRUD entries are still present in the upload queue.
                        Make sure to handle uploads and complete CRUD transactions or batches by calling and awaiting their [.complete()] method.
                        The next upload iteration will be delayed."""
                        )
                        throw Exception("Delaying due to previously encountered CRUD item.")
                    }

                        checkedCrudItem = nextCrudItem
                        uploadCrud()
                    } else {
                    // Uploading is completed
                    bucketStorage.updateLocalTarget { getWriteCheckpoint() }
                    break
                }
            } catch (e: Exception) {
                logger.e(e) { "Error uploading crud" }
                status.update(uploading = false, uploadError = e)
                delay(retryDelayMs)
                break
            }
        }
        status.update(uploading = false)
    }

    private suspend fun uploadCrudBatch(): Boolean {
        if (bucketStorage.hasCrud()) {
            status.update(uploading = true)
            uploadCrud()
            return false
        } else {
            // This isolate is the only one triggering
            return bucketStorage.updateLocalTarget { getWriteCheckpoint() }
        }
    }

    private suspend fun getWriteCheckpoint(): String {
        val credentials = connector.getCredentialsCached()
        require(credentials != null) { "Not logged in" }
        val uri = credentials.endpointUri("write-checkpoint2.json?client_id=$clientId")

        val response = httpClient.get(uri) {
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

    private suspend fun streamingSyncRequest(req: StreamingSyncRequest): Flow<String> = flow {
        val credentials = connector.getCredentialsCached()
        require(credentials != null) { "Not logged in" }

        val uri = credentials.endpointUri("sync/stream")

        val bodyJson = JsonUtil.json.encodeToString(req)

        val request = httpClient.preparePost(uri) {
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

        val state = SyncStreamState(
            targetCheckpoint = null,
            validatedCheckpoint = null,
            appliedCheckpoint = null,
            bucketSet = initialBuckets.keys.toMutableSet(),
            retry = false
        )

        bucketEntries.forEach { entry ->
            initialBuckets[entry.bucket] = entry.opId
        }

        val req = StreamingSyncRequest(
            buckets = initialBuckets.map { (bucket, after) -> BucketRequest(bucket, after) },
            clientId = clientId!!,
            parameters = params
        )

        streamingSyncRequest(req).collect { value ->
            handleInstruction(value, state)
        }

        return state
    }

    private suspend fun handleInstruction(
        jsonString: String,
        state: SyncStreamState
    ): SyncStreamState {
        val obj = JsonUtil.json.parseToJsonElement(jsonString).jsonObject
        // TODO: Clean up
        when {
            isStreamingSyncCheckpoint(obj) -> return handleStreamingSyncCheckpoint(obj, state)
            isStreamingSyncCheckpointComplete(obj) -> return handleStreamingSyncCheckpointComplete(
                state
            )

            isStreamingSyncCheckpointDiff(obj) -> return handleStreamingSyncCheckpointDiff(
                obj,
                state
            )

            isStreamingSyncData(obj) -> return handleStreamingSyncData(obj, state)
            isStreamingKeepAlive(obj) -> return handleStreamingKeepAlive(obj, state)
            else -> {
                logger.w { "Unhandled instruction $jsonString" }
                return state
            }
        }
    }

    private suspend fun handleStreamingSyncCheckpoint(
        jsonObj: JsonObject,
        state: SyncStreamState
    ): SyncStreamState {
        val checkpoint =
            JsonUtil.json.decodeFromJsonElement<Checkpoint>(jsonObj["checkpoint"] as JsonElement)
        state.targetCheckpoint = checkpoint
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

    private suspend fun handleStreamingSyncCheckpointComplete(
        state: SyncStreamState
    ): SyncStreamState {
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
        status.update(lastSyncedAt = Clock.System.now(), clearDownloadError = true)

        return state
    }

    private suspend fun handleStreamingSyncCheckpointDiff(
        jsonObj: JsonObject,
        state: SyncStreamState
    ): SyncStreamState {
        // TODO: It may be faster to just keep track of the diff, instead of the entire checkpoint
        if (state.targetCheckpoint == null) {
            throw Exception("Checkpoint diff without previous checkpoint")
        }
        val checkpointDiff =
            JsonUtil.json.decodeFromJsonElement<StreamingSyncCheckpointDiff>(jsonObj["checkpoint_diff"]!!)

        val newBuckets = mutableMapOf<String, BucketChecksum>()

        state.targetCheckpoint!!.checksums.forEach { checksum ->
            newBuckets[checksum.bucket] = checksum
        }
        checkpointDiff.updatedBuckets.forEach { checksum ->
            newBuckets[checksum.bucket] = checksum
        }

        checkpointDiff.removedBuckets.forEach { bucket -> newBuckets.remove(bucket) }

        val newCheckpoint = Checkpoint(
            lastOpId = checkpointDiff.lastOpId,
            checksums = newBuckets.values.toList(),
            writeCheckpoint = checkpointDiff.writeCheckpoint
        )

        state.targetCheckpoint = newCheckpoint

        state.bucketSet = newBuckets.keys.toMutableSet()

        val bucketsToDelete = checkpointDiff.removedBuckets
        if (bucketsToDelete.isNotEmpty()) {
            logger.i { "Remove buckets $bucketsToDelete" }
        }
        bucketStorage.removeBuckets(bucketsToDelete)
        bucketStorage.setTargetCheckpoint(state.targetCheckpoint!!)

        return state
    }

    private suspend fun handleStreamingSyncData(
        jsonObj: JsonObject,
        state: SyncStreamState
    ): SyncStreamState {
        val syncBuckets =
            listOf<SyncDataBucket>(JsonUtil.json.decodeFromJsonElement(jsonObj["data"] as JsonElement))

        bucketStorage.saveSyncData(SyncDataBatch(syncBuckets))

        return state
    }

    private suspend fun handleStreamingKeepAlive(
        jsonObj: JsonObject,
        state: SyncStreamState
    ): SyncStreamState {
        val tokenExpiresIn = (jsonObj["token_expires_in"] as JsonPrimitive).content.toInt()

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
