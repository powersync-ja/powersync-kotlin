package co.powersync.kotlin

import android.database.Cursor
import co.powersync.kotlin.db.Schema
import co.powersync.kotlin.streaming_sync.StreamingSyncRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
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
import io.requery.android.database.sqlite.SQLiteDatabase

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.Exception
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable



class PowerSyncDatabase(val database: SQLiteDatabase, val schema: Schema, val connector: PowerSyncBackendConnector): AbstractPowerSyncDatabase() {
    var sdkVersion: String? = null

    var activeHttpResponse: HttpResponse? = null
    var httpClient: HttpClient? = null;
    override suspend fun getNextCrudTransaction() {
        TODO("Not yet implemented")
    }

    // TODO how do I send Void?
    var initCompleted: CompletableDeferred<String?>? = null

    suspend fun init(){
        initCompleted = CompletableDeferred()

        readSdkVersion()
        applySchema()

        httpClient = HttpClient(CIO) {
            install(HttpTimeout)
            install(ContentNegotiation)
        }

        initCompleted?.complete(null)
    }

    suspend fun applySchema(){
        val schemaJson = Json.encodeToString<Schema>(schema)
        val query = "SELECT powersync_replace_schema(?);"
        println("Serialized app schema: $schemaJson")
        try {
            val cursor: Cursor = database.rawQuery(query, arrayOf(schemaJson))
            cursor.moveToNext()
            println("Result: ${cursor.columnNames.joinToString(" ")}")
        } catch (e: Exception) {
            println("Exception $e")
        }

    }

    suspend fun readSdkVersion() {
        val query = "SELECT powersync_rs_version();"
        val cursor: Cursor = database.rawQuery(query, null)
        cursor.moveToNext()
        val idx = cursor.getColumnIndex("powersync_rs_version()")

        if(idx < 0){
            // TODO, better error required?
            throw ArrayIndexOutOfBoundsException("Cannot read powersync sdk version, no powersync_rs_version() column in table, Columns: ${cursor.columnNames.joinToString(" ")}")
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

    suspend fun connect(){
        disconnect()

        // If isCompleted returns true, that means that init had completed (Failure also counts as completed)
        if(initCompleted?.isCompleted != true){
            initCompleted?.await()
        }

        syncIteration(connector).collect { value -> println("Instruction: $value") }
    }

    suspend fun disconnect(){
        println("Disconnecting")
        activeHttpResponse?.cancel()
    }

    suspend fun syncIteration(connector: PowerSyncBackendConnector): Flow<String> = flow {
        val newLineChar = '\n'.code.toByte()
        var instructions: Array<String> = arrayOf()

        val creds = connector.fetchCredentials()
        val psPath = creds.endpoint + "/sync/stream"

        val statement = httpClient!!.preparePost(psPath){
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Authorization, "Token ${creds.token}")
                append("User-Id", creds.userID)
            }
            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
            }
            setBody("{}")
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
                                buffer += byte;
                            }
                        }
                    }
                }

                if(instructions.size > 0){
                    instructions.forEach { instruction -> emit(instruction) }
                    instructions = arrayOf();
                }
            }
        }
    }
}