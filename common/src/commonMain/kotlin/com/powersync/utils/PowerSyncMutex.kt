package com.powersync.utils

import kotlinx.coroutines.sync.Mutex

/**
 * The subset of [Mutex] used by the PowerSync SDK.
 *
 * This mutex also has a web-specific implementation based on the web locks API.
 */
internal interface PowerSyncMutex {
    suspend fun acquire(owner: Any? = null): HeldMutex

    suspend fun tryAcquire(owner: Any? = null): HeldMutex?
}

private class LocalMutex: PowerSyncMutex {
    private val mutex = Mutex()

    override suspend fun acquire(owner: Any?): HeldMutex {
        mutex.lock(owner)

        return object : HeldMutex {
            override fun close() {
                mutex.unlock(owner)
            }
        }
    }

    override suspend fun tryAcquire(owner: Any?): HeldMutex? {
        if (!mutex.tryLock(owner)) return null

        return object : HeldMutex {
            override fun close() {
                mutex.unlock(owner)
            }
        }
    }
}

/**
 * A unique [PowerSyncMutex] that is not shared across multiple processes or tabs.
 */
internal fun localMutex(): PowerSyncMutex = LocalMutex()

/**
 * A mutex that is shared across tabs on the web (that is, [PowerSyncMutex.acquire] is serialized
 * even across tabs).
 *
 * On native and JVM targets, this returns a [localMutex].
 */
internal expect fun maybeSharedMutex(name: String): PowerSyncMutex

internal interface HeldMutex: AutoCloseable
