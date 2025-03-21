package com.powersync.db

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.sync.Mutex

/**
 * Returns an object that, when deallocated, calls [ActiveDatabaseResource.dispose].
 */
internal expect fun disposeWhenDeallocated(resource: ActiveDatabaseResource): Any

/**
 * An collection of PowerSync databases with the same path / identifier.
 *
 * We expect that each group will only ever have one database because we encourage users to write their databases as
 * singletons. We print a warning when two databases are part of the same group.
 * Additionally, we want to avoid two databases in the same group having a sync stream open at the same time to avoid
 * duplicate resources being used. For this reason, each active database group has a coroutine mutex guarding the
 * sync job.
 */
internal class ActiveDatabaseGroup(val identifier: String) {
    internal var refCount = 0 // Guarded by companion object
    internal val syncMutex = Mutex()

    fun removeUsage() {
        synchronized(ActiveDatabaseGroup) {
            if (--refCount == 0) {
                allGroups.remove(this)
            }
        }
    }

    companion object: SynchronizedObject() {
        internal val multipleInstancesMessage =
            """
            Multiple PowerSync instances for the same database have been detected.
            This can cause unexpected results.
            Please check your PowerSync client instantiation logic if this is not intentional.
            """.trimIndent()

        private val allGroups = mutableListOf<ActiveDatabaseGroup>()

        private fun findGroup(warnOnDuplicate: Logger, identifier: String): ActiveDatabaseGroup {
            return synchronized(this) {
                val existing = allGroups.asSequence().firstOrNull { it.identifier == identifier }
                val resolvedGroup = if (existing == null) {
                    val added = ActiveDatabaseGroup(identifier)
                    allGroups.add(added)
                    added
                } else {
                    existing
                }

                if (resolvedGroup.refCount++ != 0) {
                    warnOnDuplicate.w { multipleInstancesMessage }
                }

                resolvedGroup
            }
        }

        internal fun referenceDatabase(warnOnDuplicate: Logger, identifier: String): Pair<ActiveDatabaseResource, Any> {
            val group = findGroup(warnOnDuplicate, identifier)
            val resource = ActiveDatabaseResource(group)

            return resource to disposeWhenDeallocated(resource)
        }
    }
}

internal class ActiveDatabaseResource(val group: ActiveDatabaseGroup) {
    val disposed = atomic(false)

    fun dispose() {
        if (!disposed.getAndSet(true)) {
            group.removeUsage()
        }
    }
}
