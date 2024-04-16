package com.powersync

import app.cash.sqldelight.db.SqlDriver
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.ReadQueries
import com.powersync.db.WriteQueries
import com.powersync.db.crud.CrudBatch
import com.powersync.db.crud.CrudTransaction
import com.powersync.sync.SyncStatus
import com.powersync.sync.SyncStream

/**
 * A PowerSync managed database.
 *
 * Use one instance per database file.
 *
 * Use [PowerSyncDatabase.connect] to connect to the PowerSync service, to keep the local database in sync with the remote database.
 *
 * All changes to local tables are automatically recorded, whether connected or not. Once connected, the changes are uploaded.
 */
public interface PowerSyncDatabase : ReadQueries, WriteQueries {

    /**
     * The current sync status.
     */
    public val currentStatus: SyncStatus

    /**
     *  Connect to the PowerSync service, and keep the databases in sync.
     *
     *  The connection is automatically re-opened if it fails for any reason.
     *
     *  TODO: Status changes are reported on [statusStream].
     */

    public suspend fun connect(connector: PowerSyncBackendConnector)


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

    public suspend fun getNextCrudTransaction(): CrudTransaction?

    /**
     * Convenience method to get the current version of PowerSync.
     */
    public suspend fun getPowerSyncVersion(): String
}