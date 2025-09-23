package com.powersync.sync

import com.powersync.bucket.Checkpoint
import com.powersync.bucket.LocalOperationCounters
import com.powersync.bucket.StreamPriority
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.min

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

@Serializable
internal data class ProgressInfo(
    @SerialName("downloaded")
    override val downloadedOperations: Int,
    @SerialName("total")
    override val totalOperations: Int,
) : ProgressWithOperations

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
public data class SyncDownloadProgress internal constructor(
    private val buckets: Map<String, CoreBucketProgress>,
) : ProgressWithOperations {
    override val downloadedOperations: Int
    override val totalOperations: Int

    init {
        val (target, completed) = targetAndCompletedCounts(StreamPriority.FULL_SYNC_PRIORITY)
        totalOperations = target
        downloadedOperations = completed
    }

    /**
     * Creates download progress information from the local progress counters since the last full sync and the target
     * checkpoint.
     */
    @LegacySyncImplementation
    internal constructor(localProgress: Map<String, LocalOperationCounters>, target: Checkpoint) : this(
        buildMap {
            var invalidated = false

            for (entry in target.checksums) {
                val savedProgress = localProgress[entry.bucket]
                val atLast = savedProgress?.atLast ?: 0
                val sinceLast = savedProgress?.sinceLast ?: 0

                put(
                    entry.bucket,
                    CoreBucketProgress(
                        priority = entry.priority,
                        atLast = (savedProgress?.atLast ?: 0).toLong(),
                        sinceLast = (savedProgress?.sinceLast ?: 0).toLong(),
                        targetCount = (entry.count ?: 0).toLong(),
                    ),
                )

                entry.count?.let { knownCount ->
                    if (knownCount < atLast + sinceLast) {
                        // Either due to a defrag / sync rule deploy or a compaction operation, the
                        // size of the bucket shrank so much that the local ops exceed the ops in
                        // the updated bucket. We can't possibly report progress in this case (it
                        // would overshoot 100%).
                        invalidated = true
                    }
                }
            }

            if (invalidated) {
                for ((key, value) in entries) {
                    put(key, value.copy(sinceLast = 0, atLast = 0))
                }
            }
        },
    )

    /**
     * Returns download progress towards all data up until the specified [priority] being received.
     *
     * The returned [ProgressWithOperations] instance tracks the target amount of operations that need to be downloaded
     * in total and how many of them have already been received.
     */
    public fun untilPriority(priority: StreamPriority): ProgressWithOperations {
        val (total, completed) = targetAndCompletedCounts(priority)
        return ProgressInfo(totalOperations = total, downloadedOperations = completed)
    }

    @LegacySyncImplementation
    internal fun incrementDownloaded(batch: SyncDataBatch): SyncDownloadProgress =
        SyncDownloadProgress(
            buildMap {
                putAll(this@SyncDownloadProgress.buckets)

                for (bucket in batch.buckets) {
                    val previous = get(bucket.bucket) ?: continue
                    put(
                        bucket.bucket,
                        previous.copy(
                            sinceLast = min(previous.sinceLast + bucket.data.size, previous.targetCount),
                        ),
                    )
                }
            },
        )

    private fun targetAndCompletedCounts(priority: StreamPriority): Pair<Int, Int> =
        buckets.values
            .asSequence()
            .filter { it.priority >= priority }
            .fold(0L to 0L) { (prevTarget, prevCompleted), entry ->
                (prevTarget + entry.targetCount - entry.atLast) to (prevCompleted + entry.sinceLast)
            }.let { it.first.toInt() to it.second.toInt() }
}
