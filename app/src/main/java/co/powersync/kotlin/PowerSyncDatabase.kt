package co.powersync.kotlin

import android.database.Cursor
import android.os.StrictMode
import co.powersync.kotlin.bucket.BucketChecksum
import co.powersync.kotlin.bucket.BucketStorageAdapter
import co.powersync.kotlin.bucket.Checkpoint
import co.powersync.kotlin.bucket.CrudEntry
import co.powersync.kotlin.bucket.KotlinBucketStorageAdapter
import co.powersync.kotlin.bucket.SyncDataBatch
import co.powersync.kotlin.bucket.SyncDataBucket
import co.powersync.kotlin.db.Schema
import co.powersync.kotlin.streaming_sync.BucketRequest
import co.powersync.kotlin.streaming_sync.StreamingSyncCheckpointDiff
import co.powersync.kotlin.streaming_sync.StreamingSyncRequest
import co.powersync.kotlin.streaming_sync.isStreamingKeepalive
import co.powersync.kotlin.streaming_sync.isStreamingSyncCheckpoint
import co.powersync.kotlin.streaming_sync.isStreamingSyncCheckpointComplete
import co.powersync.kotlin.streaming_sync.isStreamingSyncCheckpointDiff
import co.powersync.kotlin.streaming_sync.isStreamingSyncData
import com.russhwolf.settings.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.Date


class CrudTransaction()

data class HandleJSONInstructionResult(
    var targetCheckpoint: Checkpoint?,
    var validatedCheckpoint: Checkpoint?,
    var appliedCheckpoint: Checkpoint?,

    var bucketSet: MutableSet<String>?,
    var retry: Boolean,
)

class PowerSyncDatabase(
    val dbHelper: DatabaseHelper,
    val schema: Schema,
    val connector: PowerSyncBackendConnector,
) : AbstractPowerSyncDatabase() {
    private var sdkVersion: String? = null

    private var activeHttpResponse: HttpResponse? = null
    private var httpClient: HttpClient? = null
    private val bucketStorageAdapter: BucketStorageAdapter
    private var _lastSyncedAt: Date? = null

    private var isUploadingCrud = false //TODO Thread safe??

    init {
        if (BuildConfig.DEBUG) {
            StrictMode.enableDefaults()
        }
        bucketStorageAdapter = KotlinBucketStorageAdapter(dbHelper)
    }

    /**
     * Get the next recorded transaction to upload.
     *
     * Returns null if there is no data to upload.
     *
     * Use this from the [PowerSyncBackendConnector.uploadData]` callback.
     *
     * Once the data have been successfully uploaded, call [CrudTransaction.complete] before
     * requesting the next transaction.
     *
     * Unlike [getCrudBatch], this only returns data from a single transaction at a time.
     * All data for the transaction is loaded into memory.
     */
    override suspend fun getNextCrudTransaction(): CrudTransaction? {
        val database = dbHelper.writableDatabase
        database.beginTransaction()
        try {
            val cursor = database.rawQuery(
                "SELECT id, tx_id, data FROM ps_crud ORDER BY id ASC LIMIT 1",
                null
            )
            if (!cursor.moveToFirst()) {
                return null
            }

            val txIdIndex = cursor.getColumnIndex("tx_id")
            val txId = cursor.getInt(txIdIndex)

            val all: MutableList<CrudEntry> = mutableListOf()

            println("$txId $all")

            TODO("Not yet implemented")
            database.setTransactionSuccessful()
        } catch (e: Exception) {
            throw e
        } finally {
            database.endTransaction()
            database.close()
        }


        TODO("Not yet implemented")
    }

    private var initCompleted: CompletableDeferred<String?>? = null

    private suspend fun init() {
        initCompleted = CompletableDeferred()

        readSdkVersion()
        applySchema()

        httpClient = HttpClient(CIO) {
            install(HttpTimeout)
            install(ContentNegotiation)
        }

        bucketStorageAdapter.init()

        initCompleted?.complete(null)
    }

    private fun applySchema() {
        val database = dbHelper.readableDatabase
        val schemaJson = Json.encodeToString(schema)
        val query = "SELECT powersync_replace_schema(?)"
        println("Serialized app schema: $schemaJson")
        try {
            val cursor: Cursor = database.rawQuery(query, arrayOf(schemaJson))
            cursor.moveToNext()
            println("Result: ${cursor.columnNames.joinToString(" ")}")
        } catch (e: Exception) {
            println("Exception $e")
        }

    }

    private fun readSdkVersion() {
        val database = dbHelper.readableDatabase

        val query = "SELECT powersync_rs_version()"
        val cursor: Cursor = database.rawQuery(query, null)
        cursor.moveToNext()
        val idx = cursor.getColumnIndex("powersync_rs_version()")

        if (idx < 0) {
            // TODO, better error required?
            throw ArrayIndexOutOfBoundsException(
                "Cannot read powersync sdk version, no powersync_rs_version() column in table, Columns: ${
                    cursor.columnNames.joinToString(
                        " "
                    )
                }"
            )
        }

        sdkVersion = cursor.getString(idx)
        cursor.close()
    }

    init {
        println("PowerSyncDatabase Init")
        runBlocking {
            init()
        }
    }

    suspend fun connect() {
        println("Powersync connecting")
        disconnect()

        // If isCompleted returns true, that means that init had completed (Failure also counts as completed)
        if (initCompleted?.isCompleted != true) {
            initCompleted?.await()
        }

        GlobalScope.launch (Dispatchers.IO){
            streamingSyncIteration()
        }
    }

    private fun disconnect() {
        if (activeHttpResponse == null) {
            return
        }

        println("Disconnecting existing http connection")
        activeHttpResponse?.cancel()
    }

    /**
     *  Disconnect and clear the database.
     *  Use this when logging out.
     *  The database can still be queried after this is called, but the tables
     *  would be empty.
     */
    fun disconnectAndClear() {
        val database = dbHelper.writableDatabase
        disconnect()

        // TODO DB name, verify this is necessary with extension
        database.beginTransaction()
        try {
            database.delete("ps_oplog", "1", arrayOf())
            database.delete("ps_crud", "1", arrayOf())
            database.delete("ps_buckets", "1", arrayOf())

            val existingTableRowsCursor = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name GLOB 'ps_data_*'",
                arrayOf()
            )

            val existingTableRows = mutableSetOf<String>()
            val nameIdx = existingTableRowsCursor.getColumnIndex("name")
            while (existingTableRowsCursor.moveToNext()) {
                existingTableRows.add(existingTableRowsCursor.getString(nameIdx))
            }

            if (existingTableRows.isEmpty()) {
                return
            }

            for (row in existingTableRows) {
                database.delete(row, "1", arrayOf())
            }

            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private suspend fun streamingSyncIteration() {
        val bucketEntries = bucketStorageAdapter.getBucketStates()
        val initialBuckets = mutableMapOf<String, String>()

        val state = HandleJSONInstructionResult(
            targetCheckpoint = null,
            validatedCheckpoint = null,
            appliedCheckpoint = null,
            bucketSet = initialBuckets.keys.toMutableSet(),
            retry = false
        )

        bucketEntries.forEach { entry ->
            run {
                initialBuckets[entry.bucket] = entry.op_id
            }
        }

        val req: Array<BucketRequest> =
            initialBuckets.map { (bucket, after) -> BucketRequest(bucket, after) }.toTypedArray()

        streamingSyncRequest(
            StreamingSyncRequest(
                buckets = req,
                include_checksum = true
            )
        ).collect { value ->
            run {
                handleJSONInstruction(value, state)
                if (state.retry == true) {
                    // Disconnect the current connection and re-connect with new token
                    connect()
                }
            }
        }
    }

    // TODO use the return values e.g. {retry=true}, in react native sdk, the return values were for the locks, maybe whe need proper locks here as well
    private suspend fun handleJSONInstruction(
        jsonString: String,
        state: HandleJSONInstructionResult
    ): HandleJSONInstructionResult {
        println("Received Instruction: $jsonString")
        val obj: JsonObject = Json.parseToJsonElement(jsonString) as JsonObject

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
            isStreamingKeepalive(obj) -> return handleStreamingKeepalive(obj, state)
            else -> {
                // TODO throw error?
                println("Unhandled JSON instruction")
                return state
            }
        }
    }

    suspend fun handleStreamingSyncCheckpoint(
        jsonObj: JsonObject,
        state: HandleJSONInstructionResult
    ): HandleJSONInstructionResult {
        val checkpoint =
            Json.decodeFromJsonElement<Checkpoint>(jsonObj["checkpoint"] as JsonElement)
        state.targetCheckpoint = checkpoint
        val bucketsToDelete = state.bucketSet!!.toMutableSet()
        val newBuckets = mutableSetOf<String>()

        checkpoint.buckets?.forEach { checksum ->
            run {
                newBuckets.add(checksum.bucket)
                bucketsToDelete.remove(checksum.bucket)
            }
        }

        if (bucketsToDelete.size > 0) {
            println("Removing buckets [${bucketsToDelete.joinToString(separator = ", ")}]")
        }

        state.bucketSet = newBuckets
        bucketStorageAdapter.removeBuckets(bucketsToDelete.toTypedArray())
        bucketStorageAdapter.setTargetCheckpoint(checkpoint)

        return state
    }

    suspend fun handleStreamingSyncCheckpointComplete(
        jsonObj: JsonObject,
        state: HandleJSONInstructionResult
    ): HandleJSONInstructionResult {
        println("Checkpoint complete ${Json.encodeToString(state.targetCheckpoint)}")
        val result = bucketStorageAdapter.syncLocalDatabase(state.targetCheckpoint!!)
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
            updateSyncStatus(true, Date())
        }

        state.validatedCheckpoint = state.targetCheckpoint

        return state
    }

    suspend fun handleStreamingSyncCheckpointDiff(
        jsonObj: JsonObject,
        state: HandleJSONInstructionResult
    ): HandleJSONInstructionResult {
        // TODO: It may be faster to just keep track of the diff, instead of the entire checkpoint
        if (state.targetCheckpoint == null) {
            throw Exception("Checkpoint diff without previous checkpoint")
        }
        val checkpointDiff =
            Json.decodeFromJsonElement<StreamingSyncCheckpointDiff>(jsonObj.get("checkpoint_diff")!!)

        val newBuckets = mutableMapOf<String, BucketChecksum>()

        state.targetCheckpoint!!.buckets?.forEach { checksum ->
            newBuckets[checksum.bucket] = checksum
        }
        checkpointDiff.updated_buckets.forEach { checksum ->
            newBuckets[checksum.bucket] = checksum
        }

        checkpointDiff.removed_buckets.forEach { bucket -> newBuckets.remove(bucket) }

        val newCheckpoint = Checkpoint(
            last_op_id = checkpointDiff.last_op_id,
            buckets = newBuckets.values.toTypedArray(),
            write_checkpoint = checkpointDiff.write_checkpoint
        )

        state.targetCheckpoint = newCheckpoint

        state.bucketSet = newBuckets.keys.toMutableSet()

        val bucketsToDelete = checkpointDiff.removed_buckets.clone()
        if (bucketsToDelete.isNotEmpty()) {
            println("Remove buckets $bucketsToDelete")
        }
        bucketStorageAdapter.removeBuckets(bucketsToDelete)
        bucketStorageAdapter.setTargetCheckpoint(state.targetCheckpoint!!)

        return state
    }

    suspend fun handleStreamingSyncData(
        jsonObj: JsonObject,
        state: HandleJSONInstructionResult
    ): HandleJSONInstructionResult {
        val buckets = arrayOf(SyncDataBucket.fromRow(jsonObj["data"] as JsonObject))
        bucketStorageAdapter.saveSyncData(SyncDataBatch(buckets))

        return state
    }

    suspend fun handleStreamingKeepalive(
        jsonObj: JsonObject,
        state: HandleJSONInstructionResult
    ): HandleJSONInstructionResult {
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


    private suspend fun streamingSyncRequest(req: StreamingSyncRequest): Flow<String> = flow {
        val newLineChar = '\n'.code.toByte()
        var instructions: Array<String> = arrayOf()

        val creds = connector.fetchCredentials()
        val psPath = creds.endpoint + "/sync/stream"

        val bodyJson = Json.encodeToString(req)

        val statement = httpClient!!.preparePost(psPath) {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Token ${creds.token}")
                append("User-Id", creds.userID)
            }
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
            }
            setBody(bodyJson)
        }

        statement.execute { httpResponse ->
            // TODO is this the correct way to stop/disconnect the current connection?
            activeHttpResponse = httpResponse
            val channel: ByteReadChannel = httpResponse.body()
            var buffer: ByteArray = byteArrayOf()
            while (!channel.isClosedForRead) {
                val readLimit = channel.availableForRead.coerceAtMost(DEFAULT_BUFFER_SIZE).toLong()
                val packet = channel.readRemaining(readLimit)

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

    fun updateSyncStatus(connected: Boolean, lastSyncedAt: Date? = null) {
        _lastSyncedAt = lastSyncedAt ?: _lastSyncedAt
        // TODO event firing and listeners
        //iterateListeners((cb) => cb.statusChanged?.(new SyncStatus(connected, this.lastSyncedAt)))
    }

    suspend fun triggerCrudUpload() {
        if (isUploadingCrud) {
            return
        }
        _uploadAllCrud()
    }

    // TODO make sure about thread safety
    suspend fun _uploadAllCrud() {
        isUploadingCrud = true
        while (true) {
            try {
                val done = uploadCrudBatch()
                if (done) {
                    isUploadingCrud = false
                    break
                }
            } catch (ex: Exception) {
                this.updateSyncStatus(false)
                this.isUploadingCrud = false
                break
            }
        }
    }

    private suspend fun uploadCrudBatch(): Boolean {
        val hasCrud = bucketStorageAdapter.hasCrud()
        if (hasCrud) {
            uploadCrud()
            return false
        } else {
            bucketStorageAdapter.updateLocalTarget(cb = suspend { getWriteCheckpoint() })
            return true
        }
    }

    suspend fun uploadCrud() {
        // If isCompleted returns true, that means that init had completed (Failure also counts as completed)
        if (initCompleted?.isCompleted != true) {
            initCompleted?.await()
        }

        connector.uploadData(this)
    }

    suspend fun getWriteCheckpoint(): String {
        val creds = connector.fetchCredentials()
        val psPath = creds.endpoint + "/write-checkpoint2.json"

        val response = httpClient!!.get(psPath) {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Token ${creds.token}")
                append("User-Id", creds.userID)
            }
        }
        return response.body() as String
    }
}