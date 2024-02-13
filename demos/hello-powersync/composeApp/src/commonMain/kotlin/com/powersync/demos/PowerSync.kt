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

    companion object {
        // TODO this needs to be provided by the user/dev
        private const val POWERSYNC_URL =
            "https://65a0e6bb4078d9a211d3cffb.powersync.journeyapps.com"
        private const val SUPABASE_URL = "https://wtilkjczshmzekrjelco.supabase.co"
        private const val SUPABASE_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind0aWxramN6c2htemVrcmplbGNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MDUwNDM2MTYsImV4cCI6MjAyMDYxOTYxNn0.E4DWa1ftn92_rQP-aLTsQHsZufouhMmzBfsCiX2p5eM"

        private const val TEST_EMAIL = "hello@powersync.com"
        private const val TEST_PASSWORD = "@dYX0}72eS0kT=(YG@8("
    }

    private val connector = SupabaseConnector(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY,
        powerSyncEndpoint = POWERSYNC_URL
    )
    private val database: PowerSyncDatabase =
        PowerSyncBuilder.from(driverFactory, AppSchema).build();

    private val userQueries = AppDatabase(database.driver).userQueries

    init {
        runBlocking {
            connector.login(TEST_EMAIL, TEST_PASSWORD)
            database.connect(connector)
        }
    }

    suspend fun getPowersyncVersion(): String {
        return database.getPowerSyncVersion()
    }

    suspend fun watchUsers(): Flow<List<Users>> {
        return database.watch("SELECT * FROM users", mapper = { cursor ->
            Users(
                id = cursor.getString(0)!!,
                name = cursor.getString(1)!!,
                email = cursor.getString(2)!!
            )
        })


//        return userQueries.selectAll().asFlow()
//            .mapToList(Dispatchers.IO)
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