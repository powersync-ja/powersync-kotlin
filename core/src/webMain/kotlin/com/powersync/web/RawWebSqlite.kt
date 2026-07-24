@file:JsModule("sqlite3-web")
@file:OptIn(ExperimentalWasmJsInterop::class)
package com.powersync.web

import com.powersync.internal.InternalPowerSyncAPI
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsBoolean
import kotlin.js.JsModule
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.Promise

@InternalPowerSyncAPI
public external interface WorkerHandle: JsAny {
    public val targetForErrorEvents: JsAny
    public fun postMessage(msg: JsAny, transfer: JsArray<JsAny>)
}

@InternalPowerSyncAPI
public external interface WorkerConnector: JsAny {
    public fun spawnDedicatedWorker(): WorkerHandle?
    public fun spawnSharedWorker(): WorkerHandle?
}

@InternalPowerSyncAPI
public external interface ClientInitializationOptions: JsAny {
    public val workers: WorkerConnector
    public val wasmUri: String
    public var handleCustomRequest: ((JsAny?) -> Promise<JsAny?>)?
}

@InternalPowerSyncAPI
public external interface RunFeatureDetectionOptions: JsAny {
    public val databaseName: String
}

@InternalPowerSyncAPI
public external interface ConnectOptions: JsAny {
    // TODO: Expose prepared statements cache size (other options aren't used here)
}

@InternalPowerSyncAPI
public external interface WebSqlite: JsAny {
    public fun deleteDatabase(name: String, storage: String): Promise<JsAny>
    public fun runFeatureDetection(options: RunFeatureDetectionOptions): Promise<RawFeatureDetectionResult>
    public fun connect(name: String, implementation: DatabaseImplementation, options: ConnectOptions): Promise<Database>
    public fun connectToRecommended(name: String, options: ConnectOptions): Promise<ConnectToRecommendedResult>
    public fun close()
}

@InternalPowerSyncAPI
public external interface DatabaseExecuteOptions {
    public val parameters: JsArray<JsAny?>
    public val checkInTransaction: JsBoolean
    public val token: JsNumber
    public val abort: JsAny?
}

@InternalPowerSyncAPI
public external interface BaseDatabaseResult: JsAny {
    public val autocommit: JsBoolean
    public val lastInsertRowId: JsNumber
}

@InternalPowerSyncAPI
public external interface ResultSetDatabaseResult: BaseDatabaseResult {
    public val result: ResultSet
}

@InternalPowerSyncAPI
public external interface ResultSet: JsAny {
    public val columnNames: JsArray<JsString>
    public val rows: JsArray<JsArray<JsAny?>>
    public val types: ArrayBuffer
}

@InternalPowerSyncAPI
public external interface Database: JsAny {
    public fun execute(sql: JsString, options: DatabaseExecuteOptions): Promise<BaseDatabaseResult>
    public fun select(sql: JsString, options: DatabaseExecuteOptions): Promise<ResultSetDatabaseResult>
    public fun requestLock(body: (JsNumber) -> Promise<JsAny?>): Promise<JsAny?>
    public fun customRequest(request: JsAny): Promise<JsAny?>

    public fun close(): Promise<JsAny>
}

@InternalPowerSyncAPI
public external interface ConnectToRecommendedResult: JsAny {
    public val database: Database
    public val features: RawFeatureDetectionResult
    public val implementation: DatabaseImplementation
}

@InternalPowerSyncAPI
public external interface RawExistingDatabase: JsAny {
    public val name: JsString
    public val storage: JsString
}

@InternalPowerSyncAPI
public external interface RawFeatureDetectionResult: JsAny {
    public val missingFeatures: JsArray<JsString>
    public val existingDatabases: JsArray<RawExistingDatabase>
    public val availableImplementations: JsArray<DatabaseImplementation>
}

/**
 * A supported mechanism to manage SQLite files on the web.
 *
 * Due to the large variety of browsers and the web standards they support, a number of
 * implementations are available. This library can automatically pick one after feature detection,
 * but it's also possible to open databases with a selected implementation.
 */
public external class DatabaseImplementation: JsAny {
    public val storage: String
    public val access: String

    public companion object {
        /**
         * Host an in-memory database in a shared worker.
         *
         * This isn't all that useful as it provides no persistence, but it's convenient for testing.
         */
        public val inMemoryShared: DatabaseImplementation
        /**
         * Opens a SQLite database stored in IndexedDB in a shared worker.
         */
        public val indexedDbShared: DatabaseImplementation
        /**
         * Opens a synchronous database stored in OPFS.
         *
         * The database is opened with the non-standard `readwrite-unsafe` option, and the navigator locks API is used to
         * ensure two tabs don't access the same database concurrently.
         */
        public val opfsWithExternalLocks: DatabaseImplementation
        /**
         * Opens a synchronous database stored in OPFS.
         *
         * This is similar to [opfsWithExternalLocks], but also supports browsers without `readwrite-unsafe`.
         * It works by opening file handles on most database accesses, which is substantially slower.
         */
        public val opfsWithExternalLocksWorkaround: DatabaseImplementation
        /**
         * Open a synchronous database stored in OPFS.
         *
         * This works by letting a shared worker spawn a dedicated worker. This is supposed to work according to web
         * standards, but currently only implemented in Firefox.
         */
        public val opfsShared: DatabaseImplementation
    }
}

@InternalPowerSyncAPI
public external fun defaultWorkerConnector(uri: String): WorkerConnector

@InternalPowerSyncAPI
public external fun openWebSqlite(options: ClientInitializationOptions): WebSqlite
