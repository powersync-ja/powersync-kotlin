package com.powersync.db

import co.touchlab.kermit.Logger
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.PsSqlDriver
import com.powersync.bucket.BucketPriority
import com.powersync.bucket.BucketStorage
import com.powersync.bucket.BucketStorageImpl
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.crud.CrudBatch
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudRow
import com.powersync.db.crud.CrudTransaction
import com.powersync.db.internal.InternalDatabaseImpl
import com.powersync.db.internal.InternalTable
import com.powersync.db.internal.ThrowableTransactionCallback
import com.powersync.db.schema.Schema
import com.powersync.sync.PriorityStatusEntry
import com.powersync.sync.SyncStatus
import com.powersync.sync.SyncStatusData
import com.powersync.sync.SyncStream
import com.powersync.utils.JsonParam
import com.powersync.utils.JsonUtil
import com.powersync.utils.throttle
import com.powersync.utils.toJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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
    private val internalDb = InternalDatabaseImpl(driver, scope)
    internal val bucketStorage: BucketStorage = BucketStorageImpl(internalDb, logger)

    /**
     * The current sync status.
     */
    override val currentStatus: SyncStatus = SyncStatus()

    private var syncStream: SyncStream? = null

    private var syncJob: Job? = null

    private var uploadJob: Job? = null

    init {
        runBlocking {
            val sqliteVersion = internalDb.queries.sqliteVersion().executeAsOne()
            logger.d { "SQLiteVersion: $sqliteVersion" }
            checkVersion()
            logger.d { "PowerSyncVersion: ${getPowerSyncVersion()}" }
            internalDb.queries.powersyncInit()
            applySchema()
            updateHasSynced()
        }
    }

    private suspend fun applySchema() {
        val schemaJson = JsonUtil.json.encodeToString(schema)

        internalDb.writeTransaction {
            internalDb.queries.replaceSchema(schemaJson).executeAsOne()
        }
    }

    override suspend fun connect(
        connector: PowerSyncBackendConnector,
        crudThrottleMs: Long,
        retryDelayMs: Long,
        params: Map<String, JsonParam?>,
    ) {
        // close connection if one is open
        disconnect()

        connect(
            SyncStream(
                bucketStorage = bucketStorage,
                connector = connector,
                uploadCrud = suspend { connector.uploadData(this) },
                retryDelayMs = retryDelayMs,
                logger = logger,
                params = params.toJsonObject(),
            ),
            crudThrottleMs,
        )
    }

    @OptIn(FlowPreview::class)
    internal fun connect(
        stream: SyncStream,
        crudThrottleMs: Long,
    ) {
        this.syncStream = stream
        syncJob =
            scope.launch {
                syncStream!!.streamingSync()
            }

        scope.launch {
            syncStream!!.status.asFlow().collect {
                currentStatus.update(
                    connected = it.connected,
                    connecting = it.connecting,
                    uploading = it.uploading,
                    downloading = it.downloading,
                    lastSyncedAt = it.lastSyncedAt,
                    hasSynced = it.hasSynced,
                    uploadError = it.uploadError,
                    downloadError = it.downloadError,
                    clearDownloadError = it.downloadError == null,
                    clearUploadError = it.uploadError == null,
                    priorityStatusEntries = it.priorityStatusEntries,
                )
            }
        }

        uploadJob =
            scope.launch {
                internalDb
                    .updatesOnTables()
                    .filter { it.contains(InternalTable.CRUD.toString()) }
                    .throttle(crudThrottleMs)
                    .collect {
                        syncStream!!.triggerCrudUpload()
                    }
            }
    }

    override suspend fun getCrudBatch(limit: Int): CrudBatch? {
        if (!bucketStorage.hasCrud()) {
            return null
        }

        val entries =
            internalDb.queries.getCrudEntries((limit + 1).toLong()).executeAsList().map {
                CrudEntry.fromRow(
                    CrudRow(
                        id = it.id.toString(),
                        data = it.data_!!,
                        txId = it.tx_id?.toInt(),
                    ),
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
        return internalDb.readTransaction { transaction ->
            val entry =
                bucketStorage.nextCrudItem(transaction)
                    ?: return@readTransaction null

            val txId = entry.transactionId
            val entries: List<CrudEntry> =
                if (txId == null) {
                    listOf(entry)
                } else {
                    internalDb.queries.getCrudEntryByTxId(txId.toLong()).executeAsList().map {
                        CrudEntry.fromRow(
                            CrudRow(
                                id = it.id.toString(),
                                data = it.data_!!,
                                txId = it.tx_id?.toInt(),
                            ),
                        )
                    }
                }

            return@readTransaction CrudTransaction(
                crud = entries,
                transactionId = txId,
                complete = { writeCheckpoint ->
                    logger.i { "[CrudTransaction::complete] Completing transaction with checkpoint $writeCheckpoint" }
                    handleWriteCheckpoint(entries.last().clientId, writeCheckpoint)
                },
            )
        }
    }

    override suspend fun getPowerSyncVersion(): String = internalDb.queries.powerSyncVersion().executeAsOne()

    override suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType = internalDb.get(sql, parameters, mapper)

    override suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType> = internalDb.getAll(sql, parameters, mapper)

    override suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType? = internalDb.getOptional(sql, parameters, mapper)

    override fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>?,
        throttleMs: Long?,
        mapper: (SqlCursor) -> RowType,
    ): Flow<List<RowType>> = internalDb.watch(sql, parameters, throttleMs, mapper)

    override suspend fun <R> readTransaction(callback: ThrowableTransactionCallback<R>): R = internalDb.writeTransaction(callback)

    override suspend fun <R> writeTransaction(callback: ThrowableTransactionCallback<R>): R = internalDb.writeTransaction(callback)

    override suspend fun execute(
        sql: String,
        parameters: List<Any?>?,
    ): Long = internalDb.execute(sql, parameters)

    private suspend fun handleWriteCheckpoint(
        lastTransactionId: Int,
        writeCheckpoint: String?,
    ) {
        internalDb.writeTransaction { transaction ->
            internalDb.queries.deleteEntriesWithIdLessThan(lastTransactionId.toLong())

            if (writeCheckpoint != null && !bucketStorage.hasCrud(transaction)) {
                transaction.execute(
                    "UPDATE ps_buckets SET target_op = CAST(? AS INTEGER) WHERE name='\$local'",
                    listOf(writeCheckpoint),
                )
            } else {
                transaction.execute(
                    "UPDATE ps_buckets SET target_op = CAST(? AS INTEGER) WHERE name='\$local'",
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

        if (syncStream != null) {
            syncStream?.invalidateCredentials()
            syncStream = null
        }

        currentStatus.update(
            connected = false,
            connecting = false,
            lastSyncedAt = currentStatus.lastSyncedAt,
        )
    }

    override suspend fun disconnectAndClear(clearLocal: Boolean) {
        disconnect()

        internalDb.writeTransaction {
            internalDb.queries.powersyncClear(if (clearLocal) "1" else "0").executeAsOne()
        }
        currentStatus.update(lastSyncedAt = null, hasSynced = false)
    }

    private suspend fun updateHasSynced() {
        data class SyncedAt(
            val priority: BucketPriority,
            val syncedAt: Instant?,
        )

        // Query the database to see if any data has been synced
        val syncedAtRows =
            internalDb.getAll("SELECT * FROM ps_sync_state ORDER BY priority") {
                val rawTime = it.getString(1)!!

                SyncedAt(
                    priority = BucketPriority(it.getLong(0)!!.toInt()),
                    syncedAt = rawTime.replace(" ", "T").toLocalDateTime().toInstant(TimeZone.UTC),
                )
            }

        val priorityStatus = mutableListOf<PriorityStatusEntry>()
        var lastSyncedAt: Instant? = null

        for (row in syncedAtRows) {
            if (row.priority == BucketPriority.FULL_SYNC_PRIORITY) {
                lastSyncedAt = row.syncedAt
            } else {
                priorityStatus.add(
                    PriorityStatusEntry(
                        priority = row.priority,
                        lastSyncedAt = row.syncedAt,
                        hasSynced = true,
                    ),
                )
            }
        }

        currentStatus.update(
            hasSynced = lastSyncedAt != null,
            lastSyncedAt = lastSyncedAt,
            priorityStatusEntries = priorityStatus,
        )
    }

    override suspend fun waitForFirstSync() = waitForFirstSyncImpl(null)

    override suspend fun waitForFirstSync(priority: BucketPriority) = waitForFirstSyncImpl(priority)

    private suspend fun waitForFirstSyncImpl(priority: BucketPriority?) {
        val predicate: (SyncStatusData) -> Boolean =
            if (priority == null) {
                { it.hasSynced == true }
            } else {
                { it.statusForPriority(priority).hasSynced == true }
            }

        if (predicate(currentStatus)) {
            return
        }

        currentStatus.asFlow().first(predicate)
    }

    override suspend fun close() {
        disconnect()
        internalDb.close()
    }

    /**
     * Check that a supported version of the powersync extension is loaded.
     */
    private suspend fun checkVersion() {
        val version: String =
            try {
                getPowerSyncVersion()
            } catch (e: Exception) {
                throw Exception("The powersync extension is not loaded correctly. Details: $e")
            }

        // Parse version
        val versionInts: List<Int> =
            try {
                version
                    .split(Regex("[./]"))
                    .take(3)
                    .map { it.toInt() }
            } catch (e: Exception) {
                throw Exception("Unsupported powersync extension version. Need ^0.2.0, got: $version. Details: $e")
            }

        // Validate ^0.2.0
        if (versionInts[0] != 0 || versionInts[1] < 2 || versionInts[2] < 0) {
            throw Exception("Unsupported powersync extension version. Need ^0.2.0, got: $version")
        }
    }
}
