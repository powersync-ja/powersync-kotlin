@file:OptIn(ExperimentalWasmJsInterop::class)
package com.powersync.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsBoolean
import kotlin.js.js

private external interface SharedWorkerMessage: JsAny {
    val isForSyncWorker: JsBoolean
    val message: JsAny
}

private fun createSharedWorkerMessage(forSync: Boolean, message: JsAny): SharedWorkerMessage = js("({ isForSyncWorker: forSync, message: message })")

private fun createSharedWorkerHandle(inner: WorkerHandle): WorkerHandle = js("""({ targetForErrorEvents: inner.targetForErrorEvents, postMessage(message, transfer) { inner.postMessage({ isForSyncWorker: false, message }, transfer) } })""")

/**
 * Ported from https://github.com/powersync-ja/powersync.dart/blob/f1ab64b8ed8efc2555bbf8de6826ea41c14d0295/packages/powersync/lib/src/web/worker_utils.dart#L55
 */
public class PowerSyncWorkerConnector(private val inner: WorkerConnector) {
    public constructor() : this(defaultWorkerConnector(dartWorkerUri())) {
    }

    private fun spawnDedicatedWorker(): WorkerHandle? {
        // No need to wrap this, dedicated workers are only used for databases and we don't
        // send SharedWorkerMessages to those.
        return inner.spawnDedicatedWorker()
    }

    private fun spawnSharedWorker(): WorkerHandle? {
        return inner.spawnSharedWorker()?.let(::createSharedWorkerHandle)
    }

    public fun asWorkerConnector(): WorkerConnector {
        return workerConnector(
            this::spawnDedicatedWorker,
            this::spawnSharedWorker
        )
    }
}

private fun workerConnector(
    spawnDedicated: () -> WorkerHandle?,
    spawnShared: () -> WorkerHandle?
): WorkerConnector = js("""({ spawnDedicatedWorker: spawnDedicated, spawnSharedWorker: spawnShared })""")

internal fun dartWorkerUri(): String = js("""new URL("@powersync/dart-sdk-assets/worker.js", import.meta.url).href""")
internal fun sqlite3WasmUri(): String = js("""new URL("@powersync/dart-sdk-assets/sqlite3.wasm", import.meta.url).href""")
