@file:OptIn(ExperimentalWasmJsInterop::class, InternalPowerSyncAPI::class)

package com.powersync.web

import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.internal.InternalPowerSyncAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.promise
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsBoolean
import kotlin.js.JsString
import kotlin.js.get
import kotlin.js.js
import kotlin.js.length
import kotlin.js.toJsArray
import kotlin.js.toJsBoolean
import kotlin.js.toJsString
import kotlin.js.unsafeCast

/**
 * An asynchronous connection factory opening databases hosted in a web worker.
 *
 * Note that web support in the Kotlin SDK is currently both experimental and incomplete and should
 * only be used for testing.
 *
 * To open a [SQLiteConnectionPool] that can be used with [com.powersync.PowerSyncDatabase.opened]
 * classes, use [WebConnectionFactory.openPool], e.g. like this:
 *
 * ```Kotlin
 * fun openPowerSyncDatabase(): PowerSyncDatabase {
 *   val scope = GlobalScope
 *   val identifier = "demo.db"
 *   val factory = WebConnectionFactory(scope)
 *
 *   return PowerSyncDatabase.opened(
 *     pool = factory.openPool { open(identifier) }
 *     scope = scope
 *     schema = Schema(...),
 *     identifier = identifier,
 *     logger = Logger(loggerConfigInit(platformLogWriter()))
 *   )
 * }
 * ```
 */
public class WebConnectionFactory internal constructor(
    private val coroutineScope: CoroutineScope,
    options: ClientInitializationOptions
) {

    private val sqlite = openWebSqlite(options)

    /**
     * The next id to request as update streams.
     *
     * Workers attach a stream id when sending table updates, which allows us to associate them with
     * a specific database instance.
     */
    private var nextStreamId = 0
    private val activeUpdateStreams = mutableMapOf<String, MutableSharedFlow<Set<String>>>()

    init {
        options.handleCustomRequest = { request ->
            coroutineScope.promise {
                val message = request as CustomDatabaseMessage
                if (message.rawKind.toString() == "notifyUpdates") {
                    val id = message.rawSql.toString()
                    val rawUpdatedTables = message.rawParameters.unsafeCast<JsArray<JsString>>()
                    val updatedTables = buildSet(rawUpdatedTables.length) {
                        for (i in 0..< rawUpdatedTables.length) {
                            add(rawUpdatedTables[i].toString())
                        }
                    }

                    activeUpdateStreams[id]?.emit(updatedTables)
                }

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

    private suspend fun wrapDatabase(opened: Database): WorkerConnectionPool {
        val updateStreamId = (nextStreamId++).toString()

        val pool = WorkerConnectionPool(this, updateStreamId, opened)
        activeUpdateStreams[updateStreamId] = pool.updatesController
        opened.customRequest(customRequest(
            "updateSubscriptionManagement".toJsString(),
            updateStreamId.toJsString(),
            arrayOf(true.toJsBoolean()).toJsArray(),
        )).await()

        return pool
    }

    /**
     * Opens a database with the recommended implementation.
     *
     * While the other open methods provide more control over the implementation and storage
     * location (e.g. IndexedDB or OPFS), this is a suitable default and picks a suitable
     * implementation automatically based on available browser features.
     */
    public suspend fun open(databaseName: String): SQLiteConnectionPool {
        val db = sqlite.connectToRecommended(databaseName, connectOptions()).awaitSafe()
        return wrapDatabase(db.database)
    }


    /**
     * Opens a database with a known [DatabaseImplementation].
     *
     * This should only be used if it's guaranteed that the implementation will be available, as not
     * all implementations are supported on all browsers.
     */
    public suspend fun open(databaseName: String, implementation: DatabaseImplementation): SQLiteConnectionPool {
        val db = sqlite.connect(databaseName, implementation, connectOptions()).awaitSafe()
        return wrapDatabase(db)
    }

    /**
     * Starts a feature detection for available [DatabaseImplementation]s in the current browser,
     * then runs [pickImplementation] to query with implementation to use.
     */
    public suspend fun open(databaseName: String, pickImplementation: (RawFeatureDetectionResult) -> DatabaseImplementation): SQLiteConnectionPool {
        val results = sqlite.runFeatureDetection(featureDetectionOptions(databaseName)).awaitSafe()
        val implementation = pickImplementation(results)
        return open(databaseName, implementation)
    }

    /**
     * Turns the asynchronous open methods on this class into a method that can return as connection
     * pool synchronously.
     *
     * The pool is opened in a deferred coroutine that is awaited when the pool is used.
     */
    public fun openPool(openInner: suspend WebConnectionFactory.() -> SQLiteConnectionPool): SQLiteConnectionPool {
        val asyncPool = coroutineScope.async { openInner() }
        val updates = MutableSharedFlow<Set<String>>()

        val forwardUpdates = coroutineScope.launch {
            val pool = asyncPool.await()
            updates.emitAll(pool.updates)
        }

        return object : SQLiteConnectionPool {
            override suspend fun <T> read(callback: suspend (SQLiteConnectionLease) -> T): T {
                val pool = asyncPool.await()
                return pool.read(callback)
            }

            override suspend fun <T> write(callback: suspend (SQLiteConnectionLease) -> T): T {
                val pool = asyncPool.await()
                return pool.write(callback)
            }

            override suspend fun <R> withAllConnections(action: suspend (writer: SQLiteConnectionLease, readers: List<SQLiteConnectionLease>) -> R) {
                val pool = asyncPool.await()
                return pool.withAllConnections(action)
            }

            override val updates: SharedFlow<Set<String>>
                get() = updates

            override suspend fun close() {
                asyncPool.await().close()
                forwardUpdates.cancel()
            }
        }
    }

    internal fun markClosed(db: WorkerConnectionPool) {
        activeUpdateStreams.remove(db.streamUpdatesId)
    }
}

private fun clientInitializationOptions(
    workers: WorkerConnector,
    wasmUri: String,
): ClientInitializationOptions = js("({ workers: workers, wasmUri: wasmUri, handleCustomRequest: null })")

private fun connectOptions(): ConnectOptions = js("({})")

private fun featureDetectionOptions(dbName: String): RunFeatureDetectionOptions = js("({ databaseName: dbName })")

private external interface CustomDatabaseMessage: JsAny {
    val rawKind: JsString
    val rawSql: JsString
    val rawParameters: JsAny
}

private fun customRequest(rawKind: JsString, rawSql: JsString, rawParameters: JsAny): CustomDatabaseMessage = js("({rawKind: rawKind, rawSql: rawSql, rawParameters: rawParameters})")
