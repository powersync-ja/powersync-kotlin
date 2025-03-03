package com.powersync.utils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class AtomicMutableSet<T> {
    private val mutex = Mutex()
    private val set = mutableSetOf<T>()

    public suspend fun add(element: T): Boolean =
        mutex.withLock {
            set.add(element)
        }

    public suspend fun remove(element: T): Boolean =
        mutex.withLock {
            set.remove(element)
        }

    public suspend fun clear(): Unit =
        mutex.withLock {
            set.clear()
        }

    public suspend fun contains(element: T): Boolean =
        mutex.withLock {
            set.contains(element)
        }

    public suspend fun toSet(clear: Boolean = false): Set<T> =
        mutex.withLock {
            val copied = set.toList().toSet()
            if (clear) {
                set.clear()
            }
            copied
        }
}
