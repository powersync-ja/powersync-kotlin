package co.powersync.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

import co.powersync.Closeable
import co.powersync.bucket.BucketStorage
import co.powersync.connectors.PowerSyncBackendConnector
import co.powersync.sync.SyncStatus
import co.powersync.sync.SyncStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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

        this.applySchema();
    }

    private fun applySchema() {
        val json = Json { encodeDefaults = true }
        val schemaJson = json.encodeToString(this.config.schema)
        println("Serialized app schema: $schemaJson")

        val res = createQuery("replace-schema", "SELECT powersync_replace_schema(?);", parameters = 1, binders = {
            bindString(0, schemaJson)
        }, mapper = { cursor ->
            cursor.getLong(0)!!
        }).executeAsOneOrNull()

        println("Schema replaced: $res")

    }

    suspend fun connect(connector: PowerSyncBackendConnector) {
        this.syncStream =
            SyncStream(this.bucketStorage, suspend { connector.getCredentialsCached() },
                suspend { },
                suspend { connector.uploadData(this) },
                flow {

                })

//        GlobalScope.launch(Dispatchers.IO) {
//            syncStream!!.streamingSyncIteration()
//        }
    }

    fun getPowersyncVersion(): String {
        return createQuery(
            "getPowersyncVersion",
            "SELECT powersync_rs_version()",
            mapper = { cursor ->
                cursor.getString(0)!!
            }
        ).executeAsOne()
    }

    suspend fun writeTransaction(body: TransactionWithoutReturn.() -> Unit) {
        this.database.transaction {
            body()
        }
    }

    fun <T : Any> createQuery(
        key: String,
        query: String,
        mapper: (SqlCursor) -> T,
        parameters: Int = 0,
        binders: (SqlPreparedStatement.() -> Unit)? = null,
    ): Query<T> {
        return object : Query<T>(mapper) {
            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                return driver.executeQuery(null, query, mapper, parameters, binders)
            }


            override fun addListener(listener: Listener) {
                driver.addListener(key, listener = listener)
            }

            override fun removeListener(listener: Listener) {
                driver.removeListener(key, listener = listener)
            }
        }
    }

    override suspend fun close() {
        closed = true
    }
}