package com.powersync.demos

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncBuilder
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.SupabaseConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

class PowerSync(
    driverFactory: DatabaseDriverFactory,
) {
    private val connector = SupabaseConnector()
    private val database: PowerSyncDatabase =
        PowerSyncBuilder.from(driverFactory, AppSchema).build();
    private val userQueries = AppDatabase(database.driver).userQueries

    init {
        runBlocking {
            database.connect(connector)
        }
    }

    suspend fun getPowersyncVersion(): String {
        return database.getPowerSyncVersion()
    }

    fun watchUsers(): Flow<List<Users>> {
        return userQueries.selectAll().asFlow()
            .mapToList(Dispatchers.IO)
    }

    suspend fun createUser(name: String, email: String) {
        userQueries.insertUser(name, email)
    }

    suspend fun deleteUser(id: String? = null) {
        val targetId =
            id ?: database.getOptional("SELECT id FROM users LIMIT 1", mapper = { cursor ->
                cursor.getString(0)!!
            })
            ?: return

        userQueries.deleteUser(targetId)
    }
}