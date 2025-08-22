package com.powersync.db

import co.touchlab.kermit.Logger
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.bucket.BucketPriority
import com.powersync.bucket.BucketStorage
import com.powersync.bucket.BucketStorageImpl
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.crud.CrudBatch
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudRow
import com.powersync.db.crud.CrudTransaction
import com.powersync.db.driver.SQLiteConnectionLease
import com.powersync.db.driver.SQLiteConnectionPool
import com.powersync.db.internal.InternalDatabaseImpl
import com.powersync.db.internal.InternalTable
import com.powersync.db.internal.PowerSyncVersion
import com.powersync.db.schema.Schema
import com.powersync.db.schema.toSerializable
import com.powersync.sync.PriorityStatusEntry
import com.powersync.sync.SyncOptions
import com.powersync.sync.SyncStatus
import com.powersync.sync.SyncStatusData
import com.powersync.sync.SyncStream
import com.powersync.utils.JsonParam
import com.powersync.utils.JsonUtil
import com.powersync.utils.throttle
import com.powersync.utils.toJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * A PowerSync managed database.
 *
 * Use one instance per database file.
 *
 * Use [PowerSyncDatabase.connect] to connect to the PowerSync service, to keep the local database in sync with the remote database.
 *
 * All changes to local tables are automatically recorded, whether connected or not. Once connected, the changes are uploaded.
 */
@OptIn(ExperimentalPowerSyncAPI::class)
internal class PowerSyncDatabaseImpl(
    var schema: Schema,
    val scope: CoroutineScope,
    pool: SQLiteConnectionPool,
    val logger: Logger,
    private val activeDatabaseGroup: Pair<ActiveDatabaseResource, Any>,
) : PowerSyncDatabase {
    companion object {
        internal val streamConflictMessage =
            """
            Another PowerSync client is already connected to this database.
            Multiple connections to the same database should be avoided. 
            Please check your PowerSync client instantiation logic.
            This connection attempt will be queued and will only be executed after
            currently connecting clients are disconnected.
            """.trimIndent()
    }

    override val identifier: String
        get() = activeDatabaseGroup.first.group.identifier

    private val resource = activeDatabaseGroup.first

    private val internalDb = InternalDatabaseImpl(pool)

    internal val bucketStorage: BucketStorage = BucketStorageImpl(internalDb, logger)

    override var closed = false

    /**
     * The current sync status.
     */
    override val currentStatus: SyncStatus = SyncStatus()

    private val mutex = Mutex()
    private var syncSupervisorJob: Job? = null

    // This is set before the initialization job completes
    private lateinit var powerSyncVersion: String
    private val initializeJob = scope.launch { initialize() }

    private suspend fun initialize() {
        val sqliteVersion = internalDb.get("SELECT sqlite_version()") { it.getString(0)!! }
        logger.d { "SQLiteVersion: $sqliteVersion" }
        powerSyncVersion = internalDb.get("SELECT powersync_rs_version()") { it.getString(0)!! }
        checkVersion(powerSyncVersion)
        logger.d { "PowerSyncVersion: $powerSyncVersion" }

        internalDb.writeTransaction { tx ->
            tx.getOptional("SELECT powersync_init()") {}
        }

        updateSchemaInternal(schema)
        updateHasSynced()
    }

    private suspend fun waitReady() {
        initializeJob.join()
    }

    override suspend fun updateSchema(schema: Schema) =
        runWrapped {
            waitReady()
            updateSchemaInternal(schema)
        }

    private suspend fun updateSchemaInternal(schema: Schema) {
        mutex.withLock {
            if (this.syncSupervisorJob != null) {
                throw PowerSyncException(
                    "Cannot update schema while connected",
                    cause = Exception("PowerSync client is already connected"),
                )
            }
            val schemaJson = JsonUtil.json.encodeToString(schema.toSerializable())
            internalDb.updateSchema(schemaJson)
            this.schema = schema
        }
    }

    override suspend fun connect(
        connector: PowerSyncBackendConnector,
        crudThrottleMs: Long,
        retryDelayMs: Long,
        params: Map<String, JsonParam?>,
        options: SyncOptions,
    ) {
        waitReady()
        mutex.withLock {
            disconnectInternal()

            connectInternal(crudThrottleMs) { scope ->
                SyncStream(
                    bucketStorage = bucketStorage,
                    connector = connector,
                    uploadCrud = suspend { connector.uploadData(this) },
                    retryDelayMs = retryDelayMs,
                    logger = logger,
                    params = params.toJsonObject(),
                    uploadScope = scope,
                    options = options,
                    schema = schema,
                )
            }
        }
    }

    private fun connectInternal(
        crudThrottleMs: Long,
        createStream: (CoroutineScope) -> SyncStream,
    ) {
        val db = this
        val job = SupervisorJob(scope.coroutineContext[Job])
        syncSupervisorJob = job
        var activeStream: SyncStream? = null

        scope.launch(job) {
            // Create the stream in this scope so that everything launched by the stream is bound to
            // this coroutine scope that can be cancelled independently.
            val stream = createStream(this)
            activeStream = stream

            launch {
                // Get a global lock for checking mutex maps
                val streamMutex = resource.group.syncMutex

                // Poke the streaming mutex to see if another client is using it
                var obtainedLock = false
                try {
                    // This call will throw if the lock is already held by this db client.
                    // We should never reach that point since we disconnect before connecting.
                    obtainedLock = streamMutex.tryLock(db)
                    if (!obtainedLock) {
                        // The mutex is held already by another PowerSync instance (owner).
                        // (The tryLock should throw if this client already holds the lock).
                        logger.w(streamConflictMessage)
                    }
                } catch (_: IllegalStateException) {
                    logger.e { "The streaming sync client did not disconnect before connecting" }
                }

                // This effectively queues operations
                if (!obtainedLock) {
                    // This will throw a CancellationException if the job was cancelled while waiting.
                    streamMutex.lock(db)
                }

                // We have a lock if we reached here
                try {
                    ensureActive()
                    stream.streamingSync()
                } finally {
                    streamMutex.unlock(db)
                }
            }

            launch {
                currentStatus.trackOther(stream.status)
            }

            launch {
                internalDb
                    .updatesOnTables()
                    .filter { it.contains(InternalTable.CRUD.toString()) }
                    .throttle(crudThrottleMs.milliseconds)
                    .collect {
                        stream.triggerCrudUploadAsync().join()
                    }
            }
        }

        job.invokeOnCompletion {
            if (it is DisconnectRequestedException) {
                activeStream?.invalidateCredentials()
            }
        }
    }

    override suspend fun getCrudBatch(limit: Int): CrudBatch? {
        waitReady()
        if (!bucketStorage.hasCrud()) {
            return null
        }

        var entries =
            internalDb.getAll(
                "SELECT id, tx_id, data FROM ps_crud ORDER BY id ASC LIMIT ?",
                listOf(limit.toLong() + 1),
            ) {
                CrudEntry.fromRow(
                    CrudRow(
                        id = it.getString("id"),
                        data = it.getString("data"),
                        txId = it.getLongOptional("tx_id")?.toInt(),
                    ),
                )
            }

        if (entries.isEmpty()) {
            return null
        }

        val hasMore = entries.size > limit
        if (hasMore) {
            entries = entries.dropLast(1)
        }

        return CrudBatch(entries, hasMore, complete = { writeCheckpoint ->
            handleWriteCheckpoint(entries.last().clientId, writeCheckpoint)
        })
    }

    override suspend fun getCrudTransactions(): Flow<CrudTransaction> =
        flow {
            waitReady()
            var lastItemId = -1

            // Note: We try to avoid filtering on tx_id here because there's no index on that column.
            // Starting at the first entry we want and then joining by rowid is more efficient. This is
            // sound because there can't be concurrent write transactions, so transaction ids are
            // increasing when we iterate over rowids.
            val query =
                """
                WITH RECURSIVE crud_entries AS (
                  SELECT id, tx_id, data FROM ps_crud WHERE id = (SELECT min(id) FROM ps_crud WHERE id > ?)
                  UNION ALL
                  SELECT ps_crud.id, ps_crud.tx_id, ps_crud.data FROM ps_crud
                    INNER JOIN crud_entries ON crud_entries.id + 1 = rowid
                  WHERE crud_entries.tx_id = ps_crud.tx_id
                )
                SELECT * FROM crud_entries;
                """.trimIndent()

            while (true) {
                val items = getAll(query, listOf(lastItemId), bucketStorage::mapCrudEntry)
                if (items.isEmpty()) {
                    break
                }

                val txId = items[0].transactionId
                val lastId = items.last().clientId

                lastItemId = lastId
                emit(
                    CrudTransaction(
                        crud = items,
                        transactionId = items[0].transactionId,
                        complete = { writeCheckpoint ->
                            logger.i {
                                "[CrudTransaction::complete] Completing transaction $txId (client ids until <=$lastId) with checkpoint $writeCheckpoint"
                            }
                            handleWriteCheckpoint(lastId, writeCheckpoint)
                        },
                    ),
                )
            }
        }

    override suspend fun getPowerSyncVersion(): String {
        // The initialization sets powerSyncVersion.
        waitReady()
        return powerSyncVersion
    }

    @ExperimentalPowerSyncAPI
    override suspend fun <T> useConnection(
        readOnly: Boolean,
        block: suspend (SQLiteConnectionLease) -> T
    ): T {
        waitReady()
        return internalDb.useConnection(readOnly, block)
    }


    override suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType {
        waitReady()
        return internalDb.get(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): List<RowType> {
        waitReady()
        return internalDb.getAll(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any?>?,
        mapper: (SqlCursor) -> RowType,
    ): RowType? {
        waitReady()
        return internalDb.getOptional(sql, parameters, mapper)
    }

    override fun onChange(
        tables: Set<String>,
        throttleMs: Long,
        triggerImmediately: Boolean,
    ): Flow<Set<String>> =
        flow {
            waitReady()
            emitAll(
                internalDb.onChange(tables, throttleMs, triggerImmediately),
            )
        }

    override fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any?>?,
        throttleMs: Long,
        mapper: (SqlCursor) -> RowType,
    ): Flow<List<RowType>> =
        flow {
            waitReady()
            emitAll(internalDb.watch(sql, parameters, throttleMs, mapper))
        }

    override suspend fun <R> readLock(callback: ThrowableLockCallback<R>): R {
        waitReady()
        return internalDb.readLock(callback)
    }

    override suspend fun <R> readTransaction(callback: ThrowableTransactionCallback<R>): R {
        waitReady()
        return internalDb.writeTransaction(callback)
    }

    override suspend fun <R> writeLock(callback: ThrowableLockCallback<R>): R {
        waitReady()
        return internalDb.writeLock(callback)
    }

    override suspend fun <R> writeTransaction(callback: ThrowableTransactionCallback<R>): R {
        waitReady()
        return internalDb.writeTransaction(callback)
    }

    override suspend fun execute(
        sql: String,
        parameters: List<Any?>?,
    ): Long {
        waitReady()
        return internalDb.execute(sql, parameters)
    }

    private suspend fun handleWriteCheckpoint(
        lastTransactionId: Int,
        writeCheckpoint: String?,
    ) {
        internalDb.writeTransaction { transaction ->
            transaction.execute(
                "DELETE FROM ps_crud WHERE id <= ?",
                listOf(lastTransactionId.toLong()),
            )

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
        waitReady()
        mutex.withLock { disconnectInternal() }
    }

    private suspend fun disconnectInternal() {
        val syncJob = syncSupervisorJob
        if (syncJob != null && syncJob.isActive) {
            // Using this exception type will also make the sync job invalidate credentials.
            syncJob.cancel(DisconnectRequestedException)
            syncJob.join()
            syncSupervisorJob = null
        }

        currentStatus.update {
            copy(
                connected = false,
                connecting = false,
                downloading = false,
                downloadProgress = null,
            )
        }
    }

    override suspend fun disconnectAndClear(clearLocal: Boolean) {
        disconnect()

        internalDb.writeTransaction { tx ->
            tx.getOptional("SELECT powersync_clear(?)", listOf(if (clearLocal) "1" else "0")) {}
        }
        currentStatus.update { copy(lastSyncedAt = null, hasSynced = false) }
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
                    syncedAt =
                        LocalDateTime
                            .parse(rawTime.replace(" ", "T"))
                            .toInstant(TimeZone.UTC),
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

        currentStatus.update {
            copy(
                hasSynced = lastSyncedAt != null,
                lastSyncedAt = lastSyncedAt,
                priorityStatusEntries = priorityStatus,
            )
        }
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

    override suspend fun close() =
        runWrapped {
            mutex.withLock {
                if (closed) {
                    return@withLock
                }
                initializeJob.cancelAndJoin()
                disconnectInternal()
                internalDb.close()
                resource.dispose()
                closed = true
            }
        }

    /**
     * Check that a supported version of the powersync extension is loaded.
     */
    private fun checkVersion(powerSyncVersion: String) {
        val version = PowerSyncVersion.parse(powerSyncVersion)
        if (version < PowerSyncVersion.MINIMUM) {
            PowerSyncVersion.mismatchError(powerSyncVersion)
        }
    }
}

internal object DisconnectRequestedException : CancellationException("disconnect() called")
