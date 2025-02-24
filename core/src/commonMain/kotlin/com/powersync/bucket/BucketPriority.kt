package com.powersync.bucket

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
public value class BucketPriority(
    private val priorityCode: Int,
) : Comparable<BucketPriority> {
    init {
        require(priorityCode >= 0)
    }

    override fun compareTo(other: BucketPriority): Int = other.priorityCode.compareTo(priorityCode)

    public companion object {
        internal val FULL_SYNC_PRIORITY: BucketPriority = BucketPriority(Int.MAX_VALUE)

        /**
         * The assumed priority for buckets when talking to older sync service instances that don't
         * support bucket priorities.
         */
        internal val DEFAULT_PRIORITY: BucketPriority = BucketPriority(3)
    }
}
