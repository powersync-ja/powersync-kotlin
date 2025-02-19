package com.powersync.bucket

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
public value class BucketPriority(private val priorityCode: Int): Comparable<BucketPriority> {
    init {
        require(priorityCode >= 0)
    }

    override fun compareTo(other: BucketPriority): Int {
        return other.priorityCode.compareTo(priorityCode)
    }

    public companion object {
        internal val FULL_SYNC_PRIORITY: BucketPriority = BucketPriority(Int.MAX_VALUE)
    }
}
