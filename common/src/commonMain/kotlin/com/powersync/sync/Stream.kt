package com.powersync.sync

import com.powersync.PowerSyncException
import com.powersync.bucket.StreamPriority
import kotlinx.coroutines.CancellationException
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration
import kotlin.time.Instant

public interface SyncStreamDescription {
    /**
     * THe name of the stream as it appears in the stream definition for the PowerSync service.
     */
    public val name: String

    /**
     * The parameters used to subscribe to the stream, if any.
     *
     * The same stream can be subscribed to multiple times with different parameters.
     */
    public val parameters: Map<String, Any?>?
}

/**
 * Information about a subscribed sync stream.
 *
 * This includes the [SyncStreamDescription] along with information about the current sync status.
 */
public interface SyncSubscriptionDescription : SyncStreamDescription {
    /**
     * Whether this stream is active, meaning that the subscription has been acknowledged by the
     * sync service.
     */
    public val active: Boolean

    /**
     * Whether this stream subscription is included yb default, regardless of whether the stream has
     * explicitly been subscribed to or not.
     *
     * Default streams are created by applying `auto_subscribe: true` in their definition on the
     * sync service.
     *
     * It's possible for both [isDefault] and [hasExplicitSubscription] to be true at the same time.
     * This happens when a default stream was subscribed explicitly.
     */
    public val isDefault: Boolean

    /**
     * Whether this stream been subscribed to explicitly.
     *
     * It's possible for both [isDefault] and [hasExplicitSubscription] to be true at the same time.
     * This happens when a default stream was subscribed explicitly.
     */
    public val hasExplicitSubscription: Boolean

    /**
     * For sync streams that have a time-to-live, the current time at which the stream would expire
     * if not subscribed to again.
     */
    public val expiresAt: Instant?

    /**
     * Whether this stream subscription has been synced at least once.
     */
    public val hasSynced: Boolean

    /**
     * If [hasSynced] is true, the last time data from this stream has been synced.
     */
    public val lastSyncedAt: Instant?
}

/**
 * A handle to a [SyncStreamDescription] that allows subscribing to the stream.
 *
 * To obtain an instance of [SyncStream], call [com.powersync.PowerSyncDatabase.syncStream].
 */
public interface SyncStream : SyncStreamDescription {
    @HiddenFromObjC
    public suspend fun subscribe(
        ttl: Duration? = null,
        priority: StreamPriority? = null,
    ): SyncStreamSubscription

    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun unsubscribeAll()
}

/**
 * A [SyncStream] that has been subscribed to.
 */
public interface SyncStreamSubscription : SyncStreamDescription {
    /**
     * A variant of [com.powersync.PowerSyncDatabase.waitForFirstSync] that is specific to this
     * stream subscription.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun waitForFirstSync()

    /**
     * Removes this subscription.
     *
     * Once all [SyncStreamSubscription]s for a [SyncStream] have been unsubscribed, the `ttl` for
     * that stream starts running. When it expires without subscribing again, the stream will be
     * evicted.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun unsubscribe()
}
