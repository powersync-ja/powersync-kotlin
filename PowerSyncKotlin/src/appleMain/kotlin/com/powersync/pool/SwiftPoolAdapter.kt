package com.powersync.pool

import cnames.structs.sqlite3
import com.powersync.PowerSyncException
import com.powersync.sqlite.Database
import io.ktor.utils.io.CancellationException
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * A small functional interface to provide a callback with a leased connection.
 * We use this structure in order to annotate the callback with exceptions that can be thrown.
 */
public fun interface LeaseCallback {
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun execute(lease: SwiftLeaseAdapter)
}

/**
 * A small functional interface to provide a callback leases to all connections.
 * We use this structure in order to annotate the callback with exceptions that can be thrown.
 */
public fun interface AllLeaseCallback {
    @Throws(PowerSyncException::class, CancellationException::class)
    public fun execute(
        writeLease: SwiftLeaseAdapter,
        readLeases: List<SwiftLeaseAdapter>,
    )
}

/**
 * The Swift lease will provide a SQLite connection pointer (sqlite3*) which is used in a [Database]
 */
public interface SwiftLeaseAdapter {
    public val pointer: CPointer<sqlite3>
}

/**
 *  We only allow synchronous callbacks on the Swift side for leased READ/WRITE connections.
 *  This adapter here uses synchronous callbacks.
 *  We also get a SQLite connection pointer (sqlite3*) from Swift side. which is used in a [Database]
 *  The adapter structure here is focused around easily integrating with a Swift Pool over SKIEE.
 */
public interface SwiftPoolAdapter {
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun leaseRead(callback: LeaseCallback)

    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun leaseWrite(callback: LeaseCallback)

    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun leaseAll(callback: AllLeaseCallback)

    /**
     * Passes PowerSync operations to external logic.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun processPowerSyncUpdates(updates: Set<String>)

    /**
     * Links updates from external mutations to PowerSync.
     */
    public fun linkExternalUpdates(callback: suspend (Set<String>) -> Unit)

    /**
     * Dispose any associated resources with the Pool and PowerSync.
     * We don't manage the lifecycle of the pool.
     */
    public suspend fun dispose()
}
