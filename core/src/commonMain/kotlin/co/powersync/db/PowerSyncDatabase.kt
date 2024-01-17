package co.powersync.db

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.SuspendingTransactionWithReturn
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

import co.powersync.Closeable
import co.powersync.bucket.BucketStorage
import co.powersync.connectors.PowerSyncBackendConnector
import co.powersync.db.crud.CrudEntry
import co.powersync.db.crud.CrudRow
import co.powersync.sync.SyncStatus
import co.powersync.sync.SyncStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
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

interface PowerSyncDatabaseConfig : DriverOptions {
    val driverFactory: DatabaseDriverFactory
}

open class PowerSyncDatabase(
    val config: PowerSyncDatabaseConfig
) : Closeable {
    override var closed: Boolean = false

    val driver: SqlDriver
    val database: PsDatabase
    private val bucketStorage: BucketStorage

    private var _lastSyncedAt: Instant? = null


    /**
     * The current sync status.
     */
    val currentStatus: SyncStatus

    private var syncStream: SyncStream? = null

    init {
        this.driver = config.driverFactory.createDriver(config)
        this.database = PsDatabase(driver)
        this.currentStatus = SyncStatus()

        this.bucketStorage = BucketStorage(this)

        runBlocking {
            applySchema();
        }
    }

    private suspend fun applySchema() {
        val json = Json { encodeDefaults = true }
        val schemaJson = json.encodeToString(this.config.schema)
        println("Serialized app schema: $schemaJson")

        this.writeTransaction {
            createQuery("SELECT powersync_replace_schema(?);", parameters = 1, binders = {
                bindString(0, schemaJson)
            }).executeAsOneOrNull()
        }
    }

    suspend fun connect(connector: PowerSyncBackendConnector) {
        this.syncStream =
            SyncStream(this.bucketStorage, suspend { connector.getCredentialsCached() },
                suspend { },
                suspend { connector.uploadData(this) },
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
    suspend fun getCrudBatch(limit: Int = 100) {

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
        ).executeAsList()


        /**
         *final rows = await getAll(
         *         'SELECT id, tx_id, data FROM ps_crud ORDER BY id ASC LIMIT ?',
         *         [limit + 1]);
         *     List<CrudEntry> all = [for (var row in rows) CrudEntry.fromRow(row)];
         *
         *     var haveMore = false;
         *     if (all.length > limit) {
         *       all.removeLast();
         *       haveMore = true;
         *     }
         *     if (all.isEmpty) {
         *       return null;
         *     }
         *     final last = all[all.length - 1];
         *     return CrudBatch(
         *         crud: all,
         *         haveMore: haveMore,
         *         complete: ({String? writeCheckpoint}) async {
         *           await writeTransaction((db) async {
         *             await db
         *                 .execute('DELETE FROM ps_crud WHERE id <= ?', [last.clientId]);
         *             if (writeCheckpoint != null &&
         *                 await db.getOptional('SELECT 1 FROM ps_crud LIMIT 1') == null) {
         *               await db.execute(
         *                   'UPDATE ps_buckets SET target_op = $writeCheckpoint WHERE name=\'\$local\'');
         *             } else {
         *               await db.execute(
         *                   'UPDATE ps_buckets SET target_op = $maxOpId WHERE name=\'\$local\'');
         *             }
         *           });
         *         });
         */
    }

    fun getPowersyncVersion(): String {
        return createQuery(
            "SELECT powersync_rs_version()",
            mapper = { cursor ->
                cursor.getString(0)!!
            }
        ).executeAsOne()
    }

    suspend fun <R> writeTransaction(bodyWithReturn: SuspendingTransactionWithReturn<R>.() -> R): R {
        return this.database.transactionWithResult(noEnclosing = true, bodyWithReturn)
    }

    fun createQuery(
        query: String,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): ExecutableQuery<Long> {
        return createQuery(query, { cursor -> cursor.getLong(0)!! }, parameters, binders)
    }

    fun <T : Any> createQuery(
        query: String,
        mapper: (SqlCursor) -> T,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): ExecutableQuery<T> {
        return object : ExecutableQuery<T>(mapper) {
            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                return driver.executeQuery(null, query, mapper, parameters, binders)
            }
        }
    }

    override suspend fun close() {
        closed = true
    }
}