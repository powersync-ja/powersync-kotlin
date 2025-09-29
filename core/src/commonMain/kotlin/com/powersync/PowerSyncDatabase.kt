package com.powersync

import co.touchlab.kermit.Logger
import com.powersync.bucket.BucketPriority
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.ActiveDatabaseGroup
import com.powersync.db.ActiveDatabaseResource
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.Queries
import com.powersync.db.crud.CrudBatch
import com.powersync.db.crud.CrudTransaction
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.db.driver.SingleConnectionPool
import com.powersync.db.schema.Schema
import com.powersync.sync.SyncOptions
import com.powersync.sync.SyncStatus
import com.powersync.utils.JsonParam
import com.powersync.utils.generateLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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
        options: SyncOptions = SyncOptions.defaults,
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
     * Use this from the [PowerSyncBackendConnector.uploadData] callback.
     *
     * Once the data have been successfully uploaded, call [CrudTransaction.complete] before
     * requesting the next transaction.
     *
     * Unlike [getCrudBatch], this only returns data from a single transaction at a time.
     * All data for the transaction is loaded into memory.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public suspend fun getNextCrudTransaction(): CrudTransaction? = getCrudTransactions().firstOrNull()

    /**
     * Obtains a flow emitting completed transactions with local writes against the database.

     * This is typically used from the [PowerSyncBackendConnector.uploadData] callback.
     * Each entry emitted by the returned flow is a full transaction containing all local writes
     * made while that transaction was active.
     *
     * Unlike [getNextCrudTransaction], which always returns the oldest transaction that hasn't
     * been [CrudTransaction.complete]d yet, this flow can be used to collect multiple transactions.
     * Calling [CrudTransaction.complete] will mark that and all prior transactions emitted by the
     * flow as completed.
     *
     * This can be used to upload multiple transactions in a single batch, e.g with:
     *
     * ```Kotlin
     * val batch = mutableListOf<CrudEntry>()
     * var lastTx: CrudTransaction? = null
     *
     * database.getCrudTransactions().takeWhile { batch.size < 100 }.collect {
     *   batch.addAll(it.crud)
     *   lastTx = it
     * }
     *
     * if (batch.isNotEmpty()) {
     *   uploadChanges(batch)
     *   lastTx!!.complete(null)
     * }
     * ````
     *
     * If there is no local data to upload, returns an empty flow.
     */
    public fun getCrudTransactions(): Flow<CrudTransaction>

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

    public companion object {
        /**
         * Creates a PowerSync database managed by an external connection pool.
         *
         * In this case, PowerSync will not open its own SQLite connections, but rather refer to
         * connections in the [pool].
         *
         * The `identifier` parameter should be a name identifying the path of the database. The
         * PowerSync SDK will emit a warning if multiple databases are opened with the same
         * identifier, and uses internal locks to ensure these two databases are not synced at the
         * same time (which would be inefficient and can cause consistency issues).
         */
        @ExperimentalPowerSyncAPI
        public fun opened(
            pool: SQLiteConnectionPool,
            scope: CoroutineScope,
            schema: Schema,
            identifier: String,
            logger: Logger,
        ): PowerSyncDatabase {
            val group = ActiveDatabaseGroup.referenceDatabase(logger, identifier)
            return openedWithGroup(pool, scope, schema, logger, group)
        }

        /**
         * Creates an in-memory PowerSync database instance, useful for testing.
         */
        @OptIn(ExperimentalPowerSyncAPI::class)
        public fun inMemory(
            schema: Schema,
            scope: CoroutineScope,
            logger: Logger? = null,
        ): PowerSyncDatabase {
            val logger = generateLogger(logger)
            // Since this returns a fresh in-memory database every time, use a fresh group to avoid warnings about the
            // same database being opened multiple times.
            val collection = ActiveDatabaseGroup.GroupsCollection().referenceDatabase(logger, "test")

            return openedWithGroup(
                SingleConnectionPool(openInMemoryConnection()),
                scope,
                schema,
                logger,
                collection,
            )
        }

        @ExperimentalPowerSyncAPI
        internal fun openedWithGroup(
            pool: SQLiteConnectionPool,
            scope: CoroutineScope,
            schema: Schema,
            logger: Logger,
            group: Pair<ActiveDatabaseResource, Any>,
        ): PowerSyncDatabase =
            PowerSyncDatabaseImpl(
                schema,
                scope,
                pool,
                logger,
                group,
            )
    }
}
