package co.powersync.db

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransactionWithReturn
import app.cash.sqldelight.SuspendingTransactionWithoutReturn
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import co.powersync.Closeable
import co.powersync.bucket.BucketStorage
import co.powersync.connectors.PowerSyncBackendConnector
import co.powersync.db.crud.CrudBatch
import co.powersync.db.crud.CrudEntry
import co.powersync.db.crud.CrudRow
import co.powersync.db.crud.CrudTransaction
import co.powersync.db.schema.Schema
import co.powersync.sync.SyncStatus
import co.powersync.sync.SyncStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A PowerSync managed database.
 *
 * Use one instance per database file.
 *
 * Use [PowerSyncDatabase.connect] to connect to the PowerSync service, to keep the local database in sync with the remote database.
 *
 * All changes to local tables are automatically recorded, whether connected or not. Once connected, the changes are uploaded.
 */
open class PowerSyncDatabase(
    driverFactory: DatabaseDriverFactory,
    /**
     * Schema used for the local database.
     */
    val schema: Schema,

    /**
     * Filename for the database.
     */
    val dbFilename: String

) : Closeable {
    override var closed: Boolean = false

    val driver: SqlDriver = driverFactory.createDriver(schema, dbFilename)
    val database: PsDatabase = PsDatabase(driver)
    val sqlDatabase: SqlDatabase = SqlDatabase(driver)
    private val bucketStorage: BucketStorage

    /**
     * The current sync status.
     */
    val currentStatus: SyncStatus = SyncStatus()

    private var syncStream: SyncStream? = null

    init {
        this.bucketStorage = BucketStorage(this)

        runBlocking {
            applySchema();
        }
    }

    private suspend fun applySchema() {
        val json = Json { encodeDefaults = true }
        val schemaJson = json.encodeToString(this.schema)
        println("Serialized app schema: $schemaJson")

        this.writeTransaction {
            createQuery("SELECT powersync_replace_schema(?);", parameters = 1, binders = {
                bindString(0, schemaJson)
            }).awaitAsOneOrNull()
        }
    }

    suspend fun connect(connector: PowerSyncBackendConnector) {
        this.syncStream =
            SyncStream(this.bucketStorage,
                credentialsCallback = suspend { connector.getCredentialsCached() },
                invalidCredentialsCallback = suspend { },
                uploadCrud = suspend { connector.uploadData(this) },
                flow {

                })

        GlobalScope.launch(Dispatchers.IO) {
            syncStream!!.streamingSyncIteration()
        }
    }

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
     * batch.
     *
     * This method does include transaction ids in the result, but does not group
     * data by transaction. One batch may contain data from multiple transactions,
     * and a single transaction may be split over multiple batches.
     */
    suspend fun getCrudBatch(limit: Int = 100): CrudBatch? {
        if (!bucketStorage.hasCrud()) {
            return null
        }

        val entries = createQuery(
            "SELECT id, tx_id, data FROM ps_crud ORDER BY id ASC LIMIT ?",
            parameters = 1,
            binders = { bindLong(0, (limit + 1).toLong()) },
            mapper = { cursor ->
                CrudEntry.fromRow(
                    CrudRow(
                        id = cursor.getString(0)!!,
                        data = cursor.getString(1)!!,
                        txId = cursor.getLong(2)?.toInt()
                    )
                )
            }
        ).awaitAsList()

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

    suspend fun getNextCrudTransaction(): CrudTransaction? {
        return this.readTransaction {
            val first =
                createQuery(
                    "SELECT id, tx_id, data FROM ps_crud ORDER BY id ASC LIMIT 1",
                    mapper = { cursor ->
                        CrudEntry.fromRow(
                            CrudRow(
                                id = cursor.getString(0)!!,
                                txId = cursor.getLong(1)?.toInt(),
                                data = cursor.getString(2)!!
                            )
                        )
                    }).awaitAsOneOrNull() ?: return@readTransaction null


            val txId = first.transactionId
            val entries: List<CrudEntry>
            if (txId == null) {
                entries = listOf(first)
            } else {
                entries = createQuery(
                    "SELECT id, tx_id, data FROM ps_crud WHERE tx_id = ? ORDER BY id ASC",
                    parameters = 1,
                    binders = { bindLong(0, txId.toLong()) },
                    mapper = { cursor ->
                        CrudEntry.fromRow(
                            CrudRow(
                                id = cursor.getString(0)!!,
                                txId = cursor.getLong(1)?.toInt(),
                                data = cursor.getString(2)!!,
                            )
                        )
                    }).awaitAsList()
            }

            return@readTransaction CrudTransaction(
                crud = entries, transactionId = txId,
                complete = { writeCheckpoint ->
                    handleWriteCheckpoint(entries.last().clientId, writeCheckpoint)
                }
            )
        }
    }

    private suspend fun handleWriteCheckpoint(lastTransactionId: Int, writeCheckpoint: String?) {
        writeTransaction {
            createQuery(
                "DELETE FROM ps_crud WHERE id <= ?",
                parameters = 1,
                binders = { bindLong(0, lastTransactionId.toLong()) }
            ).awaitAsOneOrNull()

            if (writeCheckpoint != null && bucketStorage.hasCrud()) {
                createQuery(
                    "UPDATE ps_buckets SET target_op = ? WHERE name='\$local'",
                    parameters = 1,
                    binders = { bindString(0, writeCheckpoint) }
                ).awaitAsOneOrNull()
            } else {
                createQuery(
                    "UPDATE ps_buckets SET target_op = ? WHERE name='\$local'",
                    parameters = 1,
                    binders = { bindString(0, bucketStorage.getMaxOpId()) }
                ).awaitAsOneOrNull()
            }
        }
    }

    fun getPowersyncVersion(): String {
        return createQuery(
            "SELECT powersync_rs_version()",
            mapper = { cursor ->
                cursor.getString(0)!!
            }
        ).executeAsOne()
    }

    suspend fun <R> readTransaction(bodyWithReturn: suspend SuspendingTransactionWithReturn<R>.() -> R): R {
        return this.database.transactionWithResult(noEnclosing = true, bodyWithReturn)
    }

    suspend fun writeTransaction(bodyNoReturn: suspend SuspendingTransactionWithoutReturn.() -> Unit) {
        this.database.transaction(noEnclosing = true, bodyNoReturn)
    }

    fun createQuery(
        query: String,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): ExecutableQuery<Long> {
        return sqlDatabase.createQuery(query, parameters, binders)
    }

    fun <T : Any> createQuery(
        query: String,
        mapper: (SqlCursor) -> T,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): ExecutableQuery<T> {
        return sqlDatabase.createQuery(query, mapper, parameters, binders)
    }

    override suspend fun close() {
        closed = true
    }
}