package co.powersync.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver

import co.powersync.Closeable
import co.powersync.bucket.BucketStorage
import co.powersync.connection.PowerSyncBackendConnector
import co.powersync.sync.SyncStatus
import co.touchlab.stately.concurrency.AtomicBoolean
import kotlinx.datetime.Instant

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

open class PowerSyncDatabase (config: PowerSyncDatabaseConfig
) : Closeable {
    override var closed: Boolean = false

    private val driver: SqlDriver
    private val database: PsDatabase
    private val bucketStorage: BucketStorage

    private var _lastSyncedAt: Instant? = null


    /**
     * The current sync status.
     */
    val currentStatus: SyncStatus

    init {
        this.driver = config.driverFactory.createDriver(config)
        this.database = PsDatabase(driver)
        this.currentStatus = SyncStatus()
        this.bucketStorage = BucketStorage(this)
    }

    suspend fun connect(connector: PowerSyncBackendConnector) {

    }

    fun getPowersyncVersion():  String {
        return createQuery("getPowersyncVersion", "SELECT powersync_rs_version()") {cursor ->
            cursor.getString(0)!!
        }.executeAsOne()
    }

    fun <T : Any> createQuery(key: String, query: String, mapper: (SqlCursor) -> T): Query<T> {
        return object : Query<T>(mapper) {
            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                return driver.executeQuery(null, query, mapper, 0, null)
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