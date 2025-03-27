package com.powersync.sync

import com.powersync.bucket.BucketPriority
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.LocalOperationCounters

/**
 * Information about a progressing download.
 *
 * This reports the [total] amount of operations to download, how many of them have already been [completed] and finally
 * a [fraction] indicating relative progress.
 *
 * To obtain a [ProgressWithOperations] instance, use a method on [SyncDownloadProgress] which in turn is available
 * on [SyncStatusData].
 */
public data class ProgressWithOperations(
    val completed: Int,
    val total: Int,
) {
    /**
     * The relative amount of [total] items that have been [completed], as a number between `0.0` and `1.0`.
     */
    public val fraction: Double get() {
        if (completed == 0) {
            return 0.0;
        }

        return completed.toDouble() / total.toDouble()
    }
}

/**
 * Provides realtime progress on how PowerSync is downloading rows.
 *
 * The reported progress always reflects the status towards the end of a sync iteration (after which a consistent
 * snapshot of all buckets is available locally).
 *
 * In rare cases (in particular, when a [compacting](https://docs.powersync.com/usage/lifecycle-maintenance/compacting-buckets)
 * operation takes place between syncs), it's possible for the returned numbers to be slightly inaccurate. For this
 * reason, [SyncDownloadProgress] should be seen as an approximation of progress. The information returned is good
 * enough to build progress bars, but not exact enough to track individual download counts.
 *
 * Also note that data is downloaded in bulk, which means that individual counters are unlikely to be updated
 * one-by-one.
 */
@ConsistentCopyVisibility
public data class SyncDownloadProgress private constructor(
    private val buckets: Map<String, BucketProgress>
) {
    /**
     * Creates download progress information from the local progress counters since the last full sync and the target
     * checkpoint.
     */
    internal constructor(localProgress: Map<String, LocalOperationCounters>, target: Checkpoint) : this(buildMap {
        for (entry in target.checksums) {
            val savedProgress = localProgress[entry.bucket]

            put(entry.bucket, BucketProgress(
                priority = entry.priority,
                atLast = savedProgress?.atLast ?: 0,
                sinceLast = savedProgress?.sinceLast ?: 0,
                targetCount = entry.count ?: 0,
            ))
        }
    })

    /**
     * Download progress towards a complete checkpoint being received.
     *
     * The returned [ProgressWithOperations] instance tracks the target amount of operations that need to be downloaded
     * in total and how many of them have already been received.
     */
    public val untilCompletion: ProgressWithOperations get() = untilPriority(BucketPriority.FULL_SYNC_PRIORITY)

    /**
     * Returns download progress towards all data up until the specified [priority] being received.
     *
     * The returned [ProgressWithOperations] instance tracks the target amount of operations that need to be downloaded
     * in total and how many of them have already been received.
     */
    public fun untilPriority(priority: BucketPriority): ProgressWithOperations {
        val (total, completed) = targetAndCompletedCounts(priority)
        return ProgressWithOperations(completed = completed, total = total)
    }

    internal fun incrementDownloaded(batch: SyncDataBatch): SyncDownloadProgress {
        return SyncDownloadProgress(buildMap {
            putAll(this@SyncDownloadProgress.buckets)

            for (bucket in batch.buckets) {
                val previous = get(bucket.bucket) ?: continue
                put(bucket.bucket, previous.copy(
                    sinceLast = previous.sinceLast + bucket.data.size
                ))
            }
        })
    }

    private fun targetAndCompletedCounts(priority: BucketPriority): Pair<Int, Int> {
        return buckets.values.asSequence()
            .filter { it.priority >= priority }
            .fold(0 to 0) { (prevTarget, prevCompleted), entry ->
                (prevTarget + entry.total) to (prevCompleted + entry.sinceLast)
            }
    }
}

private data class BucketProgress(
    val priority: BucketPriority,
    val atLast: Int,
    val sinceLast: Int,
    val targetCount: Int
) {
    val total get(): Int = targetCount - atLast
}
