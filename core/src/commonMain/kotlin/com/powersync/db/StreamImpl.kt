package com.powersync.db

import com.powersync.bucket.StreamPriority
import com.powersync.db.crud.TypedRow
import com.powersync.sync.SyncStream
import com.powersync.sync.SyncStreamSubscription
import com.powersync.utils.JsonParam
import com.powersync.utils.toJsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

internal class StreamTracker(
    val db: PowerSyncDatabaseImpl,
) {
    val groupMutex = Mutex()
    val streamGroups = mutableMapOf<StreamKey, SubscriptionGroup>()
    val currentlyReferencedStreams = MutableStateFlow(listOf<SubscriptionGroup>())

    suspend fun subscriptionsCommand(command: RustSubscriptionChangeRequest) {
        db.writeTransaction { tx ->
            tx.get("SELECT powersync_control(?,?)", listOf("subscriptions", jsonDontEncodeDefaults.encodeToString(command))) {}
        }
        db.resolveOfflineSyncStatusIfNotConnected()
    }

    internal suspend fun subscribe(
        stream: PendingStream,
        ttl: Duration?,
        priority: StreamPriority?,
    ): SyncStreamSubscription {
        val key = stream.key
        subscriptionsCommand(
            RustSubscriptionChangeRequest(
                subscribe =
                    SubscribeToStream(
                        stream = key,
                        ttl = ttl?.inWholeSeconds?.toInt(),
                        priority = priority,
                    ),
            ),
        )

        return groupMutex.withLock {
            var didAddNewGroup = false
            val group =
                streamGroups.getOrPut(key) {
                    didAddNewGroup = true
                    SubscriptionGroup(this, key)
                }

            if (didAddNewGroup) {
                val updatedStreams = streamGroups.values.toList()
                currentlyReferencedStreams.value = updatedStreams
            }

            SubscriptionImplementation(group)
        }
    }

    internal fun removeStreamGroup(key: StreamKey) {
        streamGroups.remove(key)?.also { it.active = false }
        currentlyReferencedStreams.value = streamGroups.values.toList()
    }

    private companion object {
        private val jsonDontEncodeDefaults =
            Json {
                // We don't want to encode defaults so that the RustSubscriptionChangeRequest encodes to the
                // correct enum structure with only one field set.
                encodeDefaults = false
            }
    }
}

internal class PendingStream(
    private val tracker: StreamTracker,
    override val name: String,
    val userParameters: Map<String, JsonParam?>?,
) : SyncStream {
    override val parameters: Map<String, Any?>?
        get() {
            val obj = userParameters?.toJsonObject() ?: return null
            return TypedRow(obj)
        }

    val key: StreamKey get() {
        val jsonParameters = userParameters?.toJsonObject()
        return StreamKey(name, jsonParameters)
    }

    override suspend fun subscribe(
        ttl: Duration?,
        priority: StreamPriority?,
    ): SyncStreamSubscription = tracker.subscribe(this, ttl, priority)

    override suspend fun unsubscribeAll() {
        tracker.groupMutex.withLock {
            tracker.removeStreamGroup(key)
            tracker.subscriptionsCommand(RustSubscriptionChangeRequest(unsubscribe = key))
        }
    }
}

internal class SubscriptionGroup(
    val tracker: StreamTracker,
    val key: StreamKey,
    var refcount: Int = 0,
    var active: Boolean = true,
) {
    suspend fun decrementRefCount() {
        tracker.groupMutex.withLock {
            refcount--
            if (refcount == 0 && active) {
                tracker.removeStreamGroup(key)
            }
        }
    }
}

private class SubscriptionImplementation(
    val group: SubscriptionGroup,
) : SyncStreamSubscription {
    init {
        group.refcount++
    }

    private var subscribed = false

    override val name: String
        get() = group.key.name

    override val parameters: Map<String, Any?>? = group.key.params?.let { TypedRow(it) }

    override suspend fun waitForFirstSync() {
        group.tracker.db.waitForStatusMatching { it.forStream(this)?.subscription?.hasSynced == true }
    }

    override suspend fun unsubscribe() {
        if (subscribed) {
            subscribed = false
            group.decrementRefCount()
        }
    }
}

@Serializable
internal class RustSubscriptionChangeRequest(
    // this is actually an enum with associated data, but this serializes into the form we want
    // when only a single field is set.
    val subscribe: SubscribeToStream? = null,
    val unsubscribe: StreamKey? = null,
)

@Serializable
internal class SubscribeToStream(
    val stream: StreamKey,
    val ttl: Int? = null,
    val priority: StreamPriority? = null,
)

@Serializable
internal data class StreamKey(
    val name: String,
    val params: JsonObject?,
)
