package com.powersync.bucket

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName

@Deprecated("Use StreamPriority instead")
public typealias BucketPriority = StreamPriority

@JvmInline
@Serializable
public value class StreamPriority(
    private val priorityCode: Int,
) : Comparable<StreamPriority> {
    init {
        require(priorityCode >= 0)
    }

    override fun compareTo(other: StreamPriority): Int = other.priorityCode.compareTo(priorityCode)

    public companion object Companion {
        internal val FULL_SYNC_PRIORITY: StreamPriority = StreamPriority(Int.MAX_VALUE)

        /**
         * The assumed priority for buckets when talking to older sync service instances that don't
         * support bucket priorities.
         */
        internal val DEFAULT_PRIORITY: StreamPriority = StreamPriority(3)
    }
}
