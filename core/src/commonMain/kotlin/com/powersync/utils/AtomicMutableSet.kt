package com.powersync.utils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AtomicMutableSet<T> {
    private val mutex = Mutex()
    private val set = mutableSetOf<T>()

    suspend fun add(element: T): Boolean = mutex.withLock {
        set.add(element)
    }

    suspend fun remove(element: T): Boolean = mutex.withLock {
        set.remove(element)
    }

    suspend fun clear(): Unit = mutex.withLock {
        set.clear()
    }

    suspend fun contains(element: T): Boolean = mutex.withLock {
        set.contains(element)
    }

    suspend fun toSet(): Set<T> = mutex.withLock {
        set.toSet()
    }
}
