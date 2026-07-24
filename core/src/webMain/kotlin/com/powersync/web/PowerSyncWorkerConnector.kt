@file:OptIn(ExperimentalWasmJsInterop::class, InternalPowerSyncAPI::class)
package com.powersync.web

import com.powersync.internal.InternalPowerSyncAPI
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

private fun createSharedWorkerHandle(inner: WorkerHandle): WorkerHandle = js("""({ targetForErrorEvents: inner.targetForErrorEvents, postMessage(message, transfer) { inner.postMessage({ isForSyncWorker: false, message }, transfer) } })""")

/**
 * The PowerSync worker has two responsibilities: Hosting databases and driving the sync client
 * across tabs (not used in Kotlin yet).
 *
 * Database and sync processes use different protocols, so for shared workers we need to encapsulate
 * messages in a structure indicating which service we need.
 *
 * Ported from https://github.com/powersync-ja/powersync.dart/blob/f1ab64b8ed8efc2555bbf8de6826ea41c14d0295/packages/powersync/lib/src/web/worker_utils.dart#L55
 */
public class PowerSyncWorkerConnector(private val inner: WorkerConnector) {
    public constructor() : this(defaultWorkerConnector(dartWorkerUri()))

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

internal fun dartWorkerUri(): String = js("""new URL("@powersync/dart-wasm-bundles/worker.js", import.meta.url).href""")
internal fun sqlite3WasmUri(): String = js("""new URL("@powersync/dart-wasm-bundles/sqlite3.wasm", import.meta.url).href""")
