package com.powersync.demos

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.SupabaseConnector
import com.powersync.db.DatabaseDriverFactory
import com.powersync.db.PowerSyncDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

class PowerSync(databaseDriverFactory: DatabaseDriverFactory) {
    private val database = PowerSyncDatabase(
        databaseDriverFactory, dbFilename = "powersync.db", schema = AppSchema
    )
    private val connector: PowerSyncBackendConnector = SupabaseConnector()
    private val sqlDelightDB = AppDatabase(database.driver)

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