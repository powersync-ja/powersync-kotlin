package co.powersync.sync

import co.powersync.bucket.BucketChecksum
import co.powersync.bucket.BucketRequest
import co.powersync.bucket.BucketStorage
import co.powersync.bucket.Checkpoint
import co.powersync.connection.PowerSyncCredentials
import co.touchlab.stately.concurrency.AtomicBoolean
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

class SyncStream(private val bucketStorage: BucketStorage,
                 private val credentialsCallback: suspend () -> PowerSyncCredentials?,
                 private val invalidCredentialsCallback: (suspend () -> Unit)?,
                 private val uploadCrud: suspend () -> Unit,
                 private val updateStream: Flow<Any>,
                 private val retryDelay: Long
) {
    private var isUploadingCrud = AtomicBoolean(false)

    private var lastStatus = SyncStatus()
    private val httpClient: HttpClient = HttpClient()
    private val _statusStreamController = MutableStateFlow(SyncStatus())
    val statusStream: StateFlow<SyncStatus> get() = _statusStreamController

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
        crudLoop()
        var invalidCredentials = false
        while (true) {
            _updateStatus(connecting = true)
            try {
                if (invalidCredentials && invalidCredentialsCallback != null) {
                    // This may error. In that case it will be retried again on the next
                    // iteration.
                    invalidCredentialsCallback.invoke()
                    invalidCredentials = false
                }
                streamingSyncIteration()
            } catch (e: Exception) {
                println("Error streaming sync: $e")
                invalidCredentials = true
                _updateStatus(
                    connected = false,
                    connecting = true,
                    downloading = false,
                    downloadError = e
                )
                delay(retryDelay)
            }
        }
    }

    private suspend fun crudLoop() {
        uploadAllCrud()

        updateStream.collect {
            uploadAllCrud()
        }
    }

    private suspend fun uploadAllCrud() {
        while (true) {
            try {
                val done = uploadCrudBatch()
                _updateStatus(uploadError = _noError)
                if (done) {
                    break
                }
            } catch (e: Exception) {
                println("Error uploading crud: $e")
                _updateStatus(uploading = false, uploadError = e)
                delay(retryDelay)
            }
        }
        _updateStatus(uploading = false)
    }

    private suspend fun uploadCrudBatch(): Boolean {
        if (bucketStorage.hasCrud()) {
            _updateStatus(uploading = true)
            uploadCrud()
            return false
        } else {
            // This isolate is the only one triggering
            val updated = bucketStorage.updateLocalTarget { getWriteCheckpoint() }
            if (updated) {
                TODO("Emit change")
            }
            return true
        }
    }

    private suspend fun getWriteCheckpoint(): String {
        val credentials = credentialsCallback()
        require(credentials != null) { "Not logged in" }
        val uri = credentials.endpointUri("write-checkpoint2.json")

        val response = httpClient.get(uri) {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Token ${credentials.token}")
                append("User-Id", credentials.userId)
            }
        }
        if (response.status.value == 401) {
            invalidCredentialsCallback?.invoke()
        }
        if(response.status.value != 200) {
            throw Exception("Error getting write checkpoint: ${response.status}")
        }
        val json = Json { ignoreUnknownKeys = true }
        val body = json.parseToJsonElement(response.body<String>()).jsonObject
        return body["write_checkpoint"]!!.jsonPrimitive.content
    }

    private suspend fun streamingSyncRequest(req: StreamingSyncRequest): Flow<String> = flow {
        val newLineChar = '\n'.code.toByte()
        val credentials = credentialsCallback()
        require(credentials != null) { "Not logged in" }

        val uri = credentials.endpointUri("sync/stream")

        val bodyJson = Json.encodeToString(req)

        val statement = httpClient.preparePost(uri) {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Token ${credentials.token}")
                append("User-Id", credentials.userId)
            }
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
            }
            setBody(bodyJson)
        }


        var instructions: Array<String> = arrayOf()

        statement.execute { httpResponse ->
            val channel: ByteReadChannel = httpResponse.body()
            var buffer: ByteArray = byteArrayOf()
            while (!channel.isClosedForRead) {

                val packet = channel.readRemaining()

                while (!packet.isEmpty) {
                    val bytes = packet.readBytes()
                    bytes.forEach { byte ->
                        run {
                            if (byte == newLineChar) {
                                instructions += String(buffer)
                                buffer = byteArrayOf()
                            } else {
                                buffer += byte
                            }
                        }
                    }
                }

                if (instructions.isNotEmpty()) {
                    instructions.forEach { instruction -> emit(instruction) }
                    instructions = arrayOf()
                } else {
                    // No more data to read right now and no instructions to emit, wait a bit
                    // Maybe this isn't even necessary
                    delay(1000)
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
            run {
                initialBuckets[entry.bucket] = entry.opId
            }
        }

        val req: List<BucketRequest> =
            initialBuckets.map { (bucket, after) -> BucketRequest(bucket, after) }

        streamingSyncRequest(
            StreamingSyncRequest(
                buckets = req,
            )
        ).collect { value ->
            run {
                handleInstruction(value, state)
            }
        }
    }

    private suspend fun handleInstruction(jsonString: String,
                                          state: SyncStreamState): SyncStreamState {
        println("Received Instruction: $jsonString")
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(jsonString).jsonObject

        when (true) {
            isStreamingSyncCheckpoint(obj) -> return handleStreamingSyncCheckpoint(obj, state)
            isStreamingSyncCheckpointComplete(obj) -> return handleStreamingSyncCheckpointComplete(
                obj,
                state
            )

            isStreamingSyncCheckpointDiff(obj) -> return handleStreamingSyncCheckpointDiff(
                obj,
                state
            )

            isStreamingSyncData(obj) -> return handleStreamingSyncData(obj, state)
            isStreamingKeepAlive(obj) -> return handleStreamingKeepalive(obj, state)
            else -> {
                // TODO throw error?
                println("Unhandled JSON instruction")
                return state
            }
        }
    }

    private suspend fun handleStreamingSyncCheckpoint(
        jsonObj: JsonObject,
        state: SyncStreamState
    ): SyncStreamState {
        val checkpoint = Json.decodeFromJsonElement<Checkpoint>(jsonObj["checkpoint"] as JsonElement)

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
        jsonObj: JsonObject,
        state: SyncStreamState
    ): SyncStreamState {
        println("Checkpoint complete ${Json.encodeToString(state.targetCheckpoint)}")
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
            Json.decodeFromJsonElement<StreamingSyncCheckpointDiff>(jsonObj["checkpoint_diff"]!!)

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
            writeCheckpoint =  checkpointDiff.writeCheckpoint
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
        val buckets = listOf(SyncDataBucket.fromRow(jsonObj["data"] as JsonObject))
        bucketStorage.saveSyncData(SyncDataBatch(buckets))

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

    suspend fun triggerCrudUpload() {
        if (isUploadingCrud.value) {
            return
        }
        _uploadAllCrud()
    }


    // TODO make sure about thread safety
    suspend fun _uploadAllCrud() {
        isUploadingCrud.value = true
        while (true) {
            try {
                val done = uploadCrudBatch()
                if (done) {
                    isUploadingCrud.value =  false
                    break
                }
            } catch (ex: Exception) {
                this.isUploadingCrud.value = false
                break
            }
        }
    }

    private fun _updateStatus(
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
        _statusStreamController.value = newStatus
    }

}

data class SyncStreamState(
    var targetCheckpoint: Checkpoint?,
    var validatedCheckpoint: Checkpoint?,
    var appliedCheckpoint: Checkpoint?,

    var bucketSet: MutableSet<String>?,
    var retry: Boolean,
)