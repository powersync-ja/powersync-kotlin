package co.powersync.kotlin

import android.database.Cursor
import co.powersync.kotlin.db.Schema
import io.requery.android.database.sqlite.SQLiteDatabase

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.Exception

class PowerSyncDatabase(val database: SQLiteDatabase, val schema: Schema): AbstractPowerSyncDatabase() {
    var sdkVersion: String? = null
    override suspend fun getNextCrudTransaction() {
        TODO("Not yet implemented")
    }

    // TODO how do I send Void?
    var initCompleted: CompletableDeferred<String?>? = null;

    suspend fun init(){
        initCompleted = CompletableDeferred()

        readSdkVersion()
        applySchema()

        initCompleted?.complete(null)
    }

    suspend fun applySchema(){
        val schemaJson = Json.encodeToString<Schema>(schema);
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

    suspend fun connect(connector: PowerSyncBackendConnector){
        disconnect()

        // If isCompleted returns true, that means that init had completed (Failure also counts as completed)
        if(initCompleted?.isCompleted != true){
            initCompleted?.await()
        }


    }

    suspend fun disconnect(){
        TODO("Not yet implemented")
    }
}