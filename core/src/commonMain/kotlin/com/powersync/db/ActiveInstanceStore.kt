package com.powersync.db

import co.touchlab.kermit.Logger
import co.touchlab.stately.concurrency.AtomicBoolean
import co.touchlab.stately.concurrency.Synchronizable
import co.touchlab.stately.concurrency.synchronize
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
internal class ActiveDatabaseGroup(
    val identifier: String,
    private val collection: GroupsCollection,
) {
    internal var refCount = 0 // Guarded by companion object
    internal val syncMutex = Mutex()
    internal val writeLockMutex = Mutex()

    fun removeUsage() {
        collection.synchronize {
            if (--refCount == 0) {
                collection.allGroups.remove(this)
            }
        }
    }

    internal open class GroupsCollection : Synchronizable() {
        internal val allGroups = mutableListOf<ActiveDatabaseGroup>()

        private fun findGroup(
            warnOnDuplicate: Logger,
            identifier: String,
        ): ActiveDatabaseGroup =
            synchronize {
                val existing = allGroups.asSequence().firstOrNull { it.identifier == identifier }
                val resolvedGroup =
                    if (existing == null) {
                        val added = ActiveDatabaseGroup(identifier, this)
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

        internal fun referenceDatabase(
            warnOnDuplicate: Logger,
            identifier: String,
        ): Pair<ActiveDatabaseResource, Any> {
            val group = findGroup(warnOnDuplicate, identifier)
            val resource = ActiveDatabaseResource(group)

            return resource to disposeWhenDeallocated(resource)
        }
    }

    companion object : GroupsCollection() {
        internal val multipleInstancesMessage =
            """
            Multiple PowerSync instances for the same database have been detected.
            This can cause unexpected results.
            Please check your PowerSync client instantiation logic if this is not intentional.
            """.trimIndent()
    }
}

public class ActiveDatabaseResource internal constructor(
    internal val group: ActiveDatabaseGroup,
) {
    internal val disposed = AtomicBoolean(false)

    internal fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            group.removeUsage()
        }
    }
}
