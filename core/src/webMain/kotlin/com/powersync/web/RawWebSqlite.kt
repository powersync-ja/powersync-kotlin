@file:JsModule("sqlite3-web")
@file:OptIn(ExperimentalWasmJsInterop::class)
package com.powersync.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsModule
import kotlin.js.JsStatic
import kotlin.js.JsSymbol
import kotlin.js.Promise

public external interface WorkerHandle: JsAny {
    public val targetForErrorEvents: JsAny
    public fun postMessage(msg: JsAny, transfer: JsArray<JsAny>)
}

public external interface WorkerConnector: JsAny {
    public fun spawnDedicatedWorker(): WorkerHandle?
    public fun spawnSharedWorker(): WorkerHandle?
}

public external interface ClientInitializationOptions: JsAny {
    public val workers: WorkerConnector
    public val wasmUri: String
    public val handleCustomRequest: ((JsAny?) -> Promise<JsAny?>)?
}

public external interface WebSqlite: JsAny {
    public fun deleteDatabase(name: String, storage: String): Promise<JsAny>
    public fun connectToRecommended(name: String): Promise<Database>
}

public external interface Database: JsAny {

}

public external class DatabaseImplementation: JsAny {
    public val storage: String
    public val access: String

    public companion object {
        public val inMemoryShared: DatabaseImplementation
        public val indexedDbShared: DatabaseImplementation
        public val opfsWithExternalLocks: DatabaseImplementation
        public val opfsWithExternalLocksWorkaround: DatabaseImplementation
        public val opfsShared: DatabaseImplementation
    }
}

public external fun defaultWorkerConnector(uri: String): WorkerHandle

public external fun openWebSqlite(options: ClientInitializationOptions): WebSqlite
