package co.powersync.demos

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import co.powersync.connectors.PowerSyncBackendConnector
import co.powersync.connectors.SupabaseConnector
import co.powersync.db.DatabaseDriverFactory
import co.powersync.db.PowerSyncDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

class PowerSync(databaseDriverFactory: DatabaseDriverFactory) {
    private val database = PowerSyncDatabase(
        databaseDriverFactory, dbFilename = "powersync.db", schema = AppSchema
    )
    private val connector: PowerSyncBackendConnector = SupabaseConnector()
    private val sqlDelightDB = AppDatabase(database.driver)

    private val mutableUsers: MutableStateFlow<List<Users>> = MutableStateFlow(emptyList())
    val users: StateFlow<List<Users>> = mutableUsers.asStateFlow()

    init {
        runBlocking {
            database.connect(connector)
        }
    }


    suspend fun getPowersyncVersion(): String {
        return database.getPowerSyncVersion()
    }

    fun watchUsers(): Flow<List<Users>> {
        return sqlDelightDB.userQueries.selectAll().asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun createUser(name: String, email: String) {
        sqlDelightDB.userQueries.insertUser(name, email)
    }

    suspend fun deleteUser(id: String? = null) {
        val targetId =
            id ?: database.getOptional("SELECT id FROM users LIMIT 1", mapper = { cursor ->
                cursor.getString(0)!!
            })
            ?: return

        sqlDelightDB.userQueries.deleteUser(targetId)
    }
}