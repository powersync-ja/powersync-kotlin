package com.powersync.db

import app.cash.sqldelight.SuspendingTransactionWithReturn
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneNotNull
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.bucket.BucketStorage
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.db.crud.CrudBatch
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.CrudRow
import com.powersync.db.crud.CrudTransaction
import com.powersync.db.internal.PsInternalDatabase
import com.powersync.db.schema.Schema
import com.powersync.sync.SyncStatus
import com.powersync.sync.SyncStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalJsFileName

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
    override val driver: SqlDriver = factory.createDriver(dbFilename),
) : PowerSyncDatabase {
    private val internalDb = PsInternalDatabase(driver)
    private val bucketStorage: BucketStorage = BucketStorage(internalDb)

    /**
     * The current sync status.
     */
    override val currentStatus: SyncStatus = SyncStatus()

    override var syncStream: SyncStream? = null

    init {

        runBlocking {
            applySchema();
        }
    }

    private suspend fun applySchema() {
        val json = Json { encodeDefaults = true }
        val schemaJson = json.encodeToString(schema)
        println("Serialized app schema: $schemaJson")

        this.writeTransaction {
            internalDb.queries.replaceSchema(schemaJson).awaitAsOne()
        }
    }

    override suspend fun connect(connector: PowerSyncBackendConnector) {

        val entriesFlow = internalDb.queries.getCrudEntries(100).asFlow()
            .mapToList(Dispatchers.IO)

        this.syncStream =
            SyncStream(
                this.bucketStorage,
                credentialsCallback = suspend { connector.getCredentialsCached() },
                invalidCredentialsCallback = suspend { },
                uploadCrud = suspend { connector.uploadData(this) },
                updateStream = entriesFlow
            )

        scope.launch(Dispatchers.IO) {
            syncStream!!.streamingSync()
        }
        scope.launch(Dispatchers.IO) {
            syncStream!!.crudLoop()
        }

        println("Set up sync stream")
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

        val hasMore = entries.size > limit;
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
                    handleWriteCheckpoint(entries.last().clientId, writeCheckpoint)
                }
            )
        }
    }

    override suspend fun getPowerSyncVersion(): String {
        val sqliteVersion = internalDb.queries.sqliteVersion().awaitAsOne()
        println("SQLiteVersion: $sqliteVersion")

        return internalDb.queries.powerSyncVersion().awaitAsOne()
    }

    override suspend fun <RowType : Any> get(
        sql: String,
        parameters: List<Any>?,
        mapper: (SqlCursor) -> RowType
    ): RowType {
        return internalDb.get(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> getAll(
        sql: String,
        parameters: List<Any>?,
        mapper: (SqlCursor) -> RowType
    ): List<RowType> {
        return internalDb.getAll(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> getOptional(
        sql: String,
        parameters: List<Any>?,
        mapper: (SqlCursor) -> RowType
    ): RowType? {
        return internalDb.getOptional(sql, parameters, mapper)
    }

    override suspend fun <RowType : Any> watch(
        sql: String,
        parameters: List<Any>?,
        mapper: (SqlCursor) -> RowType
    ): Flow<List<RowType>> {
        return internalDb.watch(sql, parameters, mapper)
    }


    override suspend fun <R> readTransaction(body: suspend SuspendingTransactionWithReturn<R>.() -> R): R {
        return internalDb.readTransaction(body)
    }

    override suspend fun <R> writeTransaction(body: suspend SuspendingTransactionWithReturn<R>.() -> R): R {
        return internalDb.writeTransaction(body)
    }

    override suspend fun execute(sql: String, parameters: List<Any>?): Long {
        return internalDb.execute(sql, parameters)
    }

    private suspend fun handleWriteCheckpoint(lastTransactionId: Int, writeCheckpoint: String?) {
        writeTransaction {
            internalDb.queries.deleteEntriesWithIdLessThan(lastTransactionId.toLong())

            if (writeCheckpoint != null && bucketStorage.hasCrud()) {
                execute(
                    "UPDATE ps_buckets SET target_op = ? WHERE name='\$local'",
                    listOf(writeCheckpoint),
                )
            } else {
                execute(
                    "UPDATE ps_buckets SET target_op = ? WHERE name='\$local'",
                    listOf(bucketStorage.getMaxOpId()),
                )
            }
        }
    }
}