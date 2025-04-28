package com.powersync.sync

import com.powersync.bucket.BucketPriority
import com.powersync.bucket.Checkpoint
import com.powersync.bucket.LocalOperationCounters

/**
 * Information about a progressing download.
 *
 * This reports the [totalOperations] amount of operations to download, how many of them have already been
 * [downloadedOperations] and finally a [fraction] indicating relative progress.
 *
 * To obtain a [ProgressWithOperations] instance, use a method on [SyncDownloadProgress] which in turn is available
 * on [SyncStatusData].
 */
public interface ProgressWithOperations {
    /**
     * How many operations need to be downloaded in total for the current download to complete.
     */
    public val totalOperations: Int

    /**
     * How many operations, out of [totalOperations], have already been downloaded.
    */
    public val downloadedOperations: Int

    /**
     * The relative amount of [totalOperations] to items in [downloadedOperations], as a number between `0.0` and `1.0` (inclusive).
     *
     * When this number reaches `1.0`, all changes have been received from the sync service.
     * Actually applying these changes happens before the [SyncStatusData.downloadProgress] field is
     * cleared though, so progress can stay at `1.0` for a short while before completing.
     */
    public val fraction: Float get() {
        if (totalOperations == 0) {
            return 0.0f
        }

        return downloadedOperations.toFloat() / totalOperations.toFloat()
    }
}

internal data class ProgressInfo(
    override val downloadedOperations: Int,
    override val totalOperations: Int,
): ProgressWithOperations

/**
 * Provides realtime progress on how PowerSync is downloading rows.
 *
 * This type reports progress by implementing [ProgressWithOperations], meaning that the [totalOperations],
 * [downloadedOperations] and [fraction] getters are available on this instance.
 * Additionally, it's possible to obtain the progress towards a specific priority only (instead of tracking progress for
 * the entire download) by using [untilPriority].
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
    private val buckets: Map<String, BucketProgress>,
): ProgressWithOperations {

    override val downloadedOperations: Int
    override val totalOperations: Int

    init {
        val (target, completed) = targetAndCompletedCounts(BucketPriority.FULL_SYNC_PRIORITY)
        totalOperations = target
        downloadedOperations = completed
    }

    /**
     * Creates download progress information from the local progress counters since the last full sync and the target
     * checkpoint.
     */
    internal constructor(localProgress: Map<String, LocalOperationCounters>, target: Checkpoint) : this(
        buildMap {
            for (entry in target.checksums) {
                val savedProgress = localProgress[entry.bucket]

                put(
                    entry.bucket,
                    BucketProgress(
                        priority = entry.priority,
                        atLast = savedProgress?.atLast ?: 0,
                        sinceLast = savedProgress?.sinceLast ?: 0,
                        targetCount = entry.count ?: 0,
                    ),
                )
            }
        },
    )

    /**
     * Returns download progress towards all data up until the specified [priority] being received.
     *
     * The returned [ProgressWithOperations] instance tracks the target amount of operations that need to be downloaded
     * in total and how many of them have already been received.
     */
    public fun untilPriority(priority: BucketPriority): ProgressWithOperations {
        val (total, completed) = targetAndCompletedCounts(priority)
        return ProgressInfo(totalOperations = total, downloadedOperations = completed)
    }

    internal fun incrementDownloaded(batch: SyncDataBatch): SyncDownloadProgress =
        SyncDownloadProgress(
            buildMap {
                putAll(this@SyncDownloadProgress.buckets)

                for (bucket in batch.buckets) {
                    val previous = get(bucket.bucket) ?: continue
                    put(
                        bucket.bucket,
                        previous.copy(
                            sinceLast = previous.sinceLast + bucket.data.size,
                        ),
                    )
                }
            },
        )

    private fun targetAndCompletedCounts(priority: BucketPriority): Pair<Int, Int> =
        buckets.values
            .asSequence()
            .filter { it.priority >= priority }
            .fold(0 to 0) { (prevTarget, prevCompleted), entry ->
                (prevTarget + entry.total) to (prevCompleted + entry.sinceLast)
            }
}

private data class BucketProgress(
    val priority: BucketPriority,
    val atLast: Int,
    val sinceLast: Int,
    val targetCount: Int,
) {
    val total get(): Int = targetCount - atLast
}
