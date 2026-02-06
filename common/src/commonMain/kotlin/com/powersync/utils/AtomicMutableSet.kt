package com.powersync.utils

import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.SynchronizedObject
import io.ktor.utils.io.locks.synchronized

@OptIn(InternalAPI::class)
public class AtomicMutableSet<T> : SynchronizedObject() {
    private val set = mutableSetOf<T>()

    public fun add(element: T): Boolean =
        synchronized(this) {
            return set.add(element)
        }

    public fun addAll(elements: Collection<T>): Boolean =
        synchronized(this) {
            return set.addAll(elements)
        }

    // Synchronized clear method
    public fun clear(): Unit =
        synchronized(this) {
            set.clear()
        }

    public fun toSetAndClear(): Set<T> =
        synchronized(this) {
            val copied = set.toList().toSet()
            set.clear()
            copied
        }
}
