package com.powersync

import com.powersync.bucket.BucketPriority
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.Queries
import com.powersync.db.crud.CrudBatch
import com.powersync.db.crud.CrudTransaction
import com.powersync.db.schema.Schema
import com.powersync.sync.SyncOptions
import com.powersync.sync.SyncStatus
import com.powersync.utils.JsonParam
import kotlin.coroutines.cancellation.CancellationException

/**
 * A PowerSync managed database.
 *
 * Use one instance per database file.
 *
 * Use [PowerSyncDatabase.connect] to connect to the PowerSync service, to keep the local database in sync with the remote database.
 *
 * All changes to local tables are automatically recorded, whether connected or not. Once connected, the changes are uploaded.
 */
public interface PowerSyncDatabase : Queries {
    /**
     * Indicates if the PowerSync client has been closed.
     * A new client is required after a client has been closed.
     */
    public val closed: Boolean

    /**
     * Identifies the database client.
     * This is typically the database name.
     */
    public val identifier: String

    /**
     * The current sync status.
     */
    public val currentStatus: SyncStatus

    /**
     * Replace the schema with a new version. This is for advanced use cases - typically the schema
     * should just be specified once in the constructor.
     *
     * Cannot be used while connected - this should only be called before connect.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun updateSchema(schema: Schema)

    /**
     * Suspend function that resolves when the first sync has occurred
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun waitForFirstSync()

    /**
     * Suspend function that resolves when the first sync covering at least all buckets with the
     * given [priority] (or a higher one, since those would be synchronized first) has completed.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun waitForFirstSync(priority: BucketPriority)

    /**
     *  Connect to the PowerSync service, and keep the databases in sync.
     *
     *  The connection is automatically re-opened if it fails for any reason.
     *
     *  Use @param [connector] to specify the [PowerSyncBackendConnector].
     *  Use @param [crudThrottleMs] to specify the time between CRUD operations. Defaults to 1000ms.
     *  Use @param [retryDelayMs] to specify the delay between retries after failure. Defaults to 5000ms.
     *  Use @param [params] to specify sync parameters from the client.
     *
     *  Example usage:
     *  ```
     *  val params = JsonParam.Map(
     *      mapOf(
     *          "name" to JsonParam.String("John Doe"),
     *          "age" to JsonParam.Number(30),
     *          "isStudent" to JsonParam.Boolean(false)
     *      )
     *   )
     *
     *  connect(
     *      connector = connector,
     *      crudThrottleMs = 2000L,
     *      retryDelayMs = 10000L,
     *      params = params
     *  )
     *  ```
     *  TODO: Internal Team - Status changes are reported on [statusStream].
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun connect(
        connector: PowerSyncBackendConnector,
        crudThrottleMs: Long = 1000L,
        retryDelayMs: Long = 5000L,
        params: Map<String, JsonParam?> = emptyMap(),
        options: SyncOptions = SyncOptions.defaults
    )

    /**
     * Get a batch of crud data to upload.
     *
     * Returns null if there is no data to upload.
     *
     * Use this from the [PowerSyncBackendConnector.uploadData]` callback.
     *
     * Once the data have been successfully uploaded, call [CrudBatch.complete] before
     * requesting the next batch.
     *
     * Use [limit] to specify the maximum number of updates to return in a single
     * batch. Default is 100.
     *
     * This method does include transaction ids in the result, but does not group
     * data by transaction. One batch may contain data from multiple transactions,
     * and a single transaction may be split over multiple batches.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun getCrudBatch(limit: Int = 100): CrudBatch?

    /**
     * Get the next recorded transaction to upload.
     *
     * Returns null if there is no data to upload.
     *
     * Use this from the [PowerSyncBackendConnector.uploadData]` callback.
     *
     * Once the data have been successfully uploaded, call [CrudTransaction.complete] before
     * requesting the next transaction.
     *
     * Unlike [getCrudBatch], this only returns data from a single transaction at a time.
     * All data for the transaction is loaded into memory.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun getNextCrudTransaction(): CrudTransaction?

    /**
     * Convenience method to get the current version of PowerSync.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun getPowerSyncVersion(): String

    /**
     * Close the sync connection.
     *
     * Use [connect] to connect again.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun disconnect()

    /**
     *  Disconnect and clear the database.
     *  Use this when logging out.
     *  The database can still be queried after this is called, but the tables
     *  would be empty.
     *
     * To preserve data in local-only tables, set clearLocal to false.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun disconnectAndClear(clearLocal: Boolean = true)

    /**
     * Close the database, releasing resources.
     * Also disconnects any active connection.
     *
     * Once close is called, this database cannot be used again - a new one must be constructed.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun close()
}
