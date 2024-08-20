package com.powersync.db

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlCursor
import co.touchlab.kermit.Logger
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.PsSqlDriver
import com.powersync.bucket.BucketStorage
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.crud.CrudBatch
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudRow
import com.powersync.db.crud.CrudTransaction
import com.powersync.db.internal.PsInternalDatabase
import com.powersync.db.internal.InternalTable
import com.powersync.db.internal.PowerSyncTransaction
import com.powersync.db.schema.Schema
import com.powersync.sync.SyncStatus
import com.powersync.sync.SyncStream
import com.powersync.utils.JsonUtil
import com.powersync.utils.Strings.quoteIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

/**
 * A PowerSync managed database.
 *
 * Use one instance per database file.
 *
 * Use [PowerSyncDatabase.connect] to connect to the PowerSync service, to keep the local database in sync with the remote database.
 *
 * All changes to local tables are automatically recorded, whether connected or not. Once connected, the changes are uploaded.
 */
internal class PowerSyncDatabaseImpl(
    val schema: Schema,
    val scope: CoroutineScope,
    val factory: DatabaseDriverFactory,
    private val dbFilename: String,
    val logger: Logger = Logger,
    driver: PsSqlDriver = factory.createDriver(scope, dbFilename),
) : PowerSyncDatabase {
    private val internalDb = PsInternalDatabase(driver, scope)
    private val bucketStorage: BucketStorage = BucketStorage(internalDb, logger)

    /**
     * The current sync status.
     */
    override val currentStatus: SyncStatus = SyncStatus()

    private var syncStream: SyncStream? = null

    private var syncJob: Job? = null

    private var uploadJob: Job? = null

    init {
        runBlocking {
            val sqliteVersion = internalDb.queries.sqliteVersion().awaitAsOne()
            logger.d { "SQLiteVersion: $sqliteVersion" }
            logger.d { "PowerSyncVersion: ${getPowerSyncVersion()}" }
            applySchema()
        }
    }

    private suspend fun applySchema() {
        val schemaJson = JsonUtil.json.encodeToString(schema)

        this.writeTransaction { tx ->
            internalDb.queries.replaceSchema(schemaJson).awaitAsOne()
        }
    }

    @OptIn(FlowPreview::class)
    override suspend fun connect(connector: PowerSyncBackendConnector, crudThrottleMs: Long, retryDelayMs: Long) {
        // close connection if one is open
        disconnect()

        this.syncStream =
            SyncStream(
                bucketStorage = bucketStorage,
                connector = connector,
                uploadCrud = suspend { connector.uploadData(this) },
                retryDelayMs = retryDelayMs,
                logger = logger
            )

        syncJob = scope.launch {
            syncStream!!.streamingSync()
        }

        scope.launch {
            syncStream!!.status.asFlow().collect {
                currentStatus.update(
                    connected = it.connected,
                    connecting = it.connecting,
                    downloading = it.downloading,
                    lastSyncedAt = it.lastSyncedAt,
                    uploadError = it.uploadError,
                    downloadError = it.downloadError,
                    clearDownloadError = it.downloadError == null,
                    clearUploadError = it.uploadError == null
                )
            }
        }

        uploadJob = scope.launch {
            internalDb.updatesOnTable(InternalTable.CRUD.toString()).debounce(crudThrottleMs).collect {
                syncStream!!.triggerCrudUpload()
            }
        }
    }

    override suspend fun getCrudBatch(limit: Int): CrudBatch? {
        if (!bucketStorage.hasCrud()) {
            return null
        }

        val entries = internalDb.queries.getCrudEntries((limit + 1).toLong()).awaitAsList().map {
            CrudEntry.fromRow(
                CrudRow(
                    id = it.id.toString(),
                    data = it.data_!!,
                    txId = it.tx_id?.toInt()
                )
            )
        }

        if (entries.isEmpty()) {
            return null
        }

        val hasMore = entries.size > limit
        if (hasMore) {
            entries.dropLast(entries.size - limit)
        }

        return CrudBatch(entries, hasMore, complete = { writeCheckpoint ->
            handleWriteCheckpoint(entries.last().clientId, writeCheckpoint)
        })
    }

    override suspend fun getNextCrudTransaction(): CrudTransaction? {
        return this.readTransaction {
            val firstEntry = internalDb.queries.getCrudFirstEntry().awaitAsOneOrNull()
                ?: return@readTransaction null

            val first = CrudEntry.fromRow(
                CrudRow(
                    id = firstEntry.id.toString(),
                    data = firstEntry.data_!!,
                    txId = firstEntry.tx_id?.toInt()
                )
            )

            val txId = first.transactionId
            val entries: List<CrudEntry>
            if (txId == null) {
                entries = listOf(first)
            } else {
                entries = internalDb.queries.getCrudEntryByTxId(txId.toLong()).awaitAsList().map {
                    CrudEntry.fromRow(
                        CrudRow(
                            id = it.id.toString(),
                            data = it.data_!!,
                            txId = it.tx_id?.toInt()
                        )
                    )
                }
            }

            return@readTransaction CrudTransaction(
                crud = entries, transactionId = txId,
                complete = { writeCheckpoint ->
                    logger.i { "[CrudTransaction::complete] Completing transaction with checkpoint $writeCheckpoint" }
                    handleWriteCheckpoint(entries.last().clientId, writeCheckpoint)
                }
            )
        }
    }

    override suspend fun getPowerSyncVersion(): String {
        return internalDb.queries.powerSyncVersion().awaitAsOne()
    }

    override suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): RowType {
        return internalDb.get(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): List<RowType> {
        return internalDb.getAll(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): RowType? {
        return internalDb.getOptional(sql, parameters, mapper)
    }

    override fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType
    ): Flow<List<RowType>> {
        return internalDb.watch(sql, parameters, mapper)
    }


    override suspend fun <R> readTransaction(callback: suspend (tx: PowerSyncTransaction) -> R): R {
        return internalDb.readTransaction { tx ->
            callback(tx)
        }
    }

    override suspend fun <R> writeTransaction(callback: suspend (tx: PowerSyncTransaction) -> R): R {
        return internalDb.writeTransaction { tx ->
            callback(tx)
        }
    }

    override suspend fun execute(sql: String, parameters: List<Any?>?): Long {
        return internalDb.execute(sql, parameters)
    }

    private suspend fun handleWriteCheckpoint(lastTransactionId: Int, writeCheckpoint: String?) {
        writeTransaction { tx ->
            internalDb.queries.deleteEntriesWithIdLessThan(lastTransactionId.toLong())

            if (writeCheckpoint != null && bucketStorage.hasCrud()) {
                tx.execute(
                    "UPDATE ps_buckets SET target_op = ? WHERE name='\$local'",
                    listOf(writeCheckpoint),
                )
            } else {
                tx.execute(
                    "UPDATE ps_buckets SET target_op = ? WHERE name='\$local'",
                    listOf(bucketStorage.getMaxOpId()),
                )
            }
        }
    }

    override suspend fun disconnect() {
        if (syncJob != null && syncJob!!.isActive) {
            syncJob?.cancelAndJoin()
        }

        if (uploadJob != null && uploadJob!!.isActive) {
            uploadJob?.cancelAndJoin()
        }

        if(syncStream != null) {
            syncStream?.invalidateCredentials()
            syncStream = null
        }

        currentStatus.update(connected = false, connecting = false)
    }

    override suspend fun disconnectAndClear(clearLocal: Boolean) {
        disconnect()

        this.writeTransaction { tx ->
            tx.execute("DELETE FROM ${InternalTable.OPLOG}")
            tx.execute("DELETE FROM ${InternalTable.CRUD}")
            tx.execute("DELETE FROM ${InternalTable.BUCKETS}")
            tx.execute("DELETE FROM ${InternalTable.UNTYPED}")

            val tableGlob = if (clearLocal) "ps_data_*" else "ps_data__*"
            val existingTableRows = internalDb.getExistingTableNames(tableGlob)

            for (row in existingTableRows) {
                tx.execute("DELETE FROM ${quoteIdentifier(row)}")
            }
        }
    }
}
