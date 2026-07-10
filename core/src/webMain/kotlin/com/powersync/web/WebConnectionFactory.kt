@file:OptIn(ExperimentalWasmJsInterop::class)

package com.powersync.web

import com.powersync.db.driver.SQLiteConnectionPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

public class WebConnectionFactory internal constructor(
    private val coroutineScope: CoroutineScope,
    options: ClientInitializationOptions
) {

    private val sqlite = openWebSqlite(options)

    init {
        options.handleCustomRequest = { request ->
            coroutineScope.promise {
                // TODO: Handle request
                null
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    public constructor(
        coroutineScope: CoroutineScope = GlobalScope,
        wasmUri: String = sqlite3WasmUri(),
        workers: PowerSyncWorkerConnector = PowerSyncWorkerConnector(),
    ): this(coroutineScope, clientInitializationOptions(workers.asWorkerConnector(), wasmUri))

    public suspend fun open(databaseName: String): SQLiteConnectionPool {
        val db = sqlite.connectToRecommended(databaseName, connectOptions()).await()
        return WorkerConnectionPool(db.database)
    }

    public suspend fun open(databaseName: String, implementation: DatabaseImplementation): SQLiteConnectionPool {
        val db = sqlite.connect(databaseName, implementation, connectOptions()).await()
        return WorkerConnectionPool(db)
    }

    public suspend fun open(databaseName: String, pickImplementation: (RawFeatureDetectionResult) -> DatabaseImplementation): SQLiteConnectionPool {
        val results = sqlite.runFeatureDetection(featureDetectionOptions(databaseName)).await()
        val implementation = pickImplementation(results)
        return open(databaseName, implementation)
    }
}

private fun clientInitializationOptions(
    workers: WorkerConnector,
    wasmUri: String,
): ClientInitializationOptions = js("({ workers: workers, wasmUri: wasmUri, handleCustomRequest: null })")

private fun connectOptions(): ConnectOptions = js("({})")

private fun featureDetectionOptions(dbName: String): RunFeatureDetectionOptions = js("({ databaseName: dbName })")
