package com.powersync.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Helper class for executing methods exclusively
internal open class ExclusiveMethodProvider {
    // Class level mutexes
    private val mapLock = Mutex()
    private val mutexMap = mutableMapOf<String, Mutex>()

    companion object {
        // global level mutexes for global exclusivity
        private val staticMapLock = Mutex()
        private val staticMutexes = mutableMapOf<String, Mutex>()

        // Runs the callback exclusively on the global level
        internal suspend fun <R> globallyExclusive(
            lockName: String,
            callback: suspend () -> R,
        ) = globalMutexFor(lockName).withLock { callback() }

        internal suspend fun globalMutexFor(lockName: String): Mutex =
            staticMapLock.withLock {
                staticMutexes.getOrPut(lockName) { Mutex() }
            }
    }

    // A method for running a callback exclusively on the class instance level
    internal suspend fun <R> exclusiveMethod(
        lockName: String,
        callback: suspend () -> R,
    ): R = mutexFor(lockName).withLock { callback() }

    internal suspend fun mutexFor(lockName: String): Mutex =
        mapLock.withLock {
            mutexMap.getOrPut(lockName) { Mutex() }
        }
}
