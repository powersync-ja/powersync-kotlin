package co.powersync.kotlin.bucket

class SyncLocalDatabaseResult (
    val checkpointValid: Boolean,
    val ready: Boolean,
    val failures: Array<Any>?
)

class BucketChecksum (
    val Bucket: String,
    /**
     * 32-bit unsigned hash.
     */
    val Checksum: Int,

    /**
     * Count of operations - informational only.
     */
    val Count: Number
)

class Checkpoint (
    val last_op_id: String,
val buckets: Array<BucketChecksum>,
val write_checkpoint: String?
)

class BucketState (
    val bucket: String,
val op_id: String
)

abstract class BucketStorageAdapter {
    abstract suspend fun init()
    abstract suspend fun saveSyncData(batch: SyncDataBatch)
    abstract suspend fun removeBuckets(buckets: Array<String>)

    // operation = change to data, checkpoint = where in the oplog we are
    abstract suspend fun setTargetCheckpoint(checkpoint: Checkpoint)

    abstract fun startSession()

    abstract suspend fun getBucketStates(): Array<BucketState>

    abstract suspend fun syncLocalDatabase(checkpoint: Checkpoint): SyncLocalDatabaseResult

    abstract suspend fun hasCrud(): Boolean
    abstract suspend fun getCrudBatch(limit: Int?): CrudBatch?

    abstract suspend fun hasCompletedSync(): Boolean
    abstract suspend fun updateLocalTarget(cb: () -> String): Boolean

    /**
     * Exposed for tests only.
     */
    abstract suspend fun autoCompact()

    /**
     * Exposed for tests only.
     */
    abstract suspend fun forceCompact()

    abstract fun getMaxOpId(): String
}