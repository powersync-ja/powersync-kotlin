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
 * A collection of PowerSync databases with the same path / identifier.
 *
 * We expect that each group will only ever have one database because we encourage users to write their databases as
 * singletons. We print a warning when two databases are part of the same group.
 * Additionally, we want to avoid two databases in the same group having a sync stream open at the same time to avoid
 * duplicate resources being used. For this reason, each active database group has a coroutine mutex guarding the
 * sync job.
 */
public class ActiveDatabaseGroup internal constructor(
    internal val identifier: String,
    private val collection: GroupsCollection,
) {
    internal var refCount = 0 // Guarded by companion object
    internal val syncMutex = Mutex()
    internal val writeLockMutex = Mutex()

    internal fun removeUsage() {
        collection.synchronize {
            if (--refCount == 0) {
                collection.allGroups.remove(this)
            }
        }
    }

    /**
     * A collection of [ActiveDatabaseGroup]s.
     *
     * Typically, one uses the singleton instance that is the companion object of that class, but separate groups can be
     * used for testing.
     */
    public open class GroupsCollection : Synchronizable() {
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

        public fun referenceDatabase(
            warnOnDuplicate: Logger,
            identifier: String,
        ): Pair<ActiveDatabaseResource, Any> {
            val group = findGroup(warnOnDuplicate, identifier)
            val resource = ActiveDatabaseResource(group)

            return resource to disposeWhenDeallocated(resource)
        }
    }

    public companion object : GroupsCollection() {
        internal val multipleInstancesMessage =
            """
            Multiple PowerSync instances for the same database have been detected.
            This can cause unexpected results.
            Please check your PowerSync client instantiation logic if this is not intentional.
            """.trimIndent()
    }
}

public class ActiveDatabaseResource(
    internal val group: ActiveDatabaseGroup,
) {
    internal val disposed = AtomicBoolean(false)

    public fun dispose() {
        if (disposed.compareAndSet(expected = false, new = true)) {
            group.removeUsage()
        }
    }
}
