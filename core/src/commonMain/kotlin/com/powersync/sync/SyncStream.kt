package com.powersync.sync

import com.powersync.bucket.BucketChecksum
import com.powersync.bucket.BucketRequest
import com.powersync.bucket.BucketStorage
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.WriteCheckpointResponse
import co.touchlab.stately.concurrency.AtomicBoolean
import com.powersync.connectors.PowerSyncBackendConnector
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
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class SyncStream(
    private val bucketStorage: BucketStorage,
    private val connector: PowerSyncBackendConnector,
    private val uploadCrud: suspend () -> Unit,
    private val retryDelay: Long = 1000L
) {
    private var isUploadingCrud = AtomicBoolean(false)

    private var lastStatus = SyncStatus()
    private val httpClient: HttpClient = HttpClient {
        install(HttpTimeout)
        install(ContentNegotiation)
    };
    private val statusStreamController = MutableStateFlow(SyncStatus())

    companion object {
        private val _noError = Any()

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

    suspend fun streamingSync() {
        var invalidCredentials = false
        while (true) {
            updateStatus(connecting = true)
            try {
                if (invalidCredentials) {
                    // This may error. In that case it will be retried again on the next
                    // iteration.
                    connector.invalidateCredentials()
                    invalidCredentials = false
                }
                streamingSyncIteration()
            } catch (e: Exception) {
                println("SyncStream::streamingSync Error: $e")
                invalidCredentials = true
                updateStatus(
                    connected = false,
                    connecting = true,
                    downloading = false,
                    downloadError = e
                )
                delay(retryDelay)
            }
        }
    }

    suspend fun triggerCrudUpload() {
        if (isUploadingCrud.value) {
            return
        }
        isUploadingCrud.value = true
        uploadAllCrud()
        isUploadingCrud.value = false
    }

    private suspend fun uploadAllCrud() {
        while (true) {
            try {
                val done = uploadCrudBatch()
                updateStatus(uploadError = _noError)
                if (done) {
                    break
                }
            } catch (e: Exception) {
                println("[SyncStream::uploadAllCrud] Error uploading crud: $e")
                updateStatus(uploading = false, uploadError = e)
                delay(retryDelay)
                break
            }
        }
        updateStatus(uploading = false)
    }

    private suspend fun uploadCrudBatch(): Boolean {
        if (bucketStorage.hasCrud()) {
            updateStatus(uploading = true)
            uploadCrud()
            return false
        } else {
            // This isolate is the only one triggering
            bucketStorage.updateLocalTarget { getWriteCheckpoint() }
            return true
        }
    }

    private suspend fun getWriteCheckpoint(): String {
        val credentials = connector.getCredentialsCached()
        require(credentials != null) { "Not logged in" }
        val uri = credentials.endpointUri("write-checkpoint2.json")

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
            val channel: ByteReadChannel = httpResponse.body()

            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line()
                if (line != null) {
                    emit(line)
                }
            }
        }
    }

    private suspend fun streamingSyncIteration() {
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
        )

        streamingSyncRequest(req).retryWhen { cause, attempt ->
            println("SyncStream::streamingSyncIteration Error: $cause")
            delay(retryDelay)
            println("SyncStream::streamingSyncIteration Retrying attempt: $attempt")
            true
        }.collect { value ->
            handleInstruction(value, state)
        }
    }

    private suspend fun handleInstruction(
        jsonString: String,
        state: SyncStreamState
    ): SyncStreamState {
        println("[SyncStream::handleInstruction] Received Instruction: $jsonString")
        val obj = JsonUtil.json.parseToJsonElement(jsonString).jsonObject

        // TODO: Clean up
        when (true) {
            isStreamingSyncCheckpoint(obj) -> return handleStreamingSyncCheckpoint(obj, state)
            isStreamingSyncCheckpointComplete(obj) -> return handleStreamingSyncCheckpointComplete(
                state
            )

            isStreamingSyncCheckpointDiff(obj) -> return handleStreamingSyncCheckpointDiff(
                obj,
                state
            )

            isStreamingSyncData(obj) -> return handleStreamingSyncData(obj, state)
            isStreamingKeepAlive(obj) -> return handleStreamingKeepalive(obj, state)
            else -> {
                println("Unhandled instruction")
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
            println("Removing buckets [${bucketsToDelete.joinToString(separator = ", ")}]")
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
            println("validated checkpoint ${state.appliedCheckpoint}")
        }

        state.validatedCheckpoint = state.targetCheckpoint

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
            println("Remove buckets $bucketsToDelete")
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

    suspend fun handleStreamingKeepalive(
        jsonObj: JsonObject,
        state: SyncStreamState
    ): SyncStreamState {
        val tokenExpiresIn = (jsonObj["token_expires_in"] as JsonPrimitive).content.toInt()

        if (tokenExpiresIn <= 0) {
            // Connection would be closed automatically right after this
            println("Token expiring reconnect")
            state.retry = true
            return state
        }
        triggerCrudUpload()
        return state
    }

    private fun updateStatus(
        lastSyncedAt: Instant? = null,
        connected: Boolean? = null,
        connecting: Boolean? = null,
        downloading: Boolean? = null,
        uploading: Boolean? = null,
        uploadError: Any? = null,
        downloadError: Any? = null,
    ) {
        val c = connected ?: lastStatus.connected
        val newStatus = SyncStatus(
            connected = c,
            connecting = !c && (connecting ?: lastStatus.connecting),
            lastSyncedAt = lastSyncedAt ?: lastStatus.lastSyncedAt,
            downloading = downloading ?: lastStatus.downloading,
            uploading = uploading ?: lastStatus.uploading,
            uploadError = if (uploadError == _noError) null else uploadError
                ?: lastStatus.uploadError,
            downloadError = if (downloadError == _noError) null else downloadError
                ?: lastStatus.downloadError
        )
        lastStatus = newStatus
        statusStreamController.value = newStatus
    }

}

data class SyncStreamState(
    var targetCheckpoint: Checkpoint?,
    var validatedCheckpoint: Checkpoint?,
    var appliedCheckpoint: Checkpoint?,

    var bucketSet: MutableSet<String>?,
    var retry: Boolean,
)