package co.powersync.demos

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class PowerSync(databaseDriverFactory: DatabaseDriverFactory) {
    private val backgroundDispatcher = Dispatchers.IO
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

    suspend fun activate() {
        observe()
    }

    private suspend fun observe() {

        sqlDelightDB.userQueries.selectAll().asFlow()
            .mapToList(Dispatchers.IO)
            .collect { userList ->
                mutableUsers.update { userList }
            }
    }

    fun getPowersyncVersion(): String {
        return database.getPowersyncVersion()
    }

    suspend fun getUsers(): List<Users> {
        return database.sqlDatabase.getAll(
            "SELECT * FROM users",
            mapper = { cursor ->
                Users(
                    id = cursor.getString(0)!!,
                    name = cursor.getString(1)!!,
                    email = cursor.getString(2)!!
                )
            })
    }

    suspend fun watchUsers(): Flow<List<Users>> {
        val q = database.sqlDatabase.watchQuery("users",
            "SELECT * FROM users",
            mapper = { cursor ->
                Users(
                    id = cursor.getString(0)!!,
                    name = cursor.getString(1)!!,
                    email = cursor.getString(2)!!
                )
            })

        q.addListener(listener = {
            println("SELECT Query changed")
        })
        return q.asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun createUser(name: String, email: String) {

        sqlDelightDB.userQueries.insertUser(name, email)

//        withContext(backgroundDispatcher) {
//            database.sqlDatabase.execute(
//                "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)", listOf(name, email)
//            )
//        }
    }

    suspend fun deleteUser(id: String? = null) {
        withContext(backgroundDispatcher) {
            val targetId =
                id ?: database.createQuery("SELECT id FROM users LIMIT 1", mapper = { cursor ->
                    cursor.getString(0)!!
                }).executeAsOneOrNull()
                ?: return@withContext
            database.createQuery(
                "DELETE FROM users WHERE id = ?",
                parameters = 1,
                binders = {
                    bindString(0, targetId)
                }).awaitAsOneOrNull()
        }
    }
}