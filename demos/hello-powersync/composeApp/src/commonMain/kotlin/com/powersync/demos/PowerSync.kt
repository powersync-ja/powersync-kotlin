package com.powersync.demos

import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncBuilder
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.SupabaseConnector
import com.powersync.demos.SupabaseConfig.POWERSYNC_URL
import com.powersync.demos.SupabaseConfig.SUPABASE_KEY
import com.powersync.demos.SupabaseConfig.SUPABASE_URL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

class PowerSync(
    driverFactory: DatabaseDriverFactory,
) {
    companion object {
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

    init {
        runBlocking {
            connector.login(TEST_EMAIL, TEST_PASSWORD)
            database.connect(connector)
        }
    }

    suspend fun getPowersyncVersion(): String {
        return database.getPowerSyncVersion()
    }

    fun watchUsers(): Flow<List<User>> {
        return database.watch("SELECT * FROM users", mapper = { cursor ->
            User(
                id = cursor.getString(0)!!,
                name = cursor.getString(1)!!,
                email = cursor.getString(2)!!
            )
        })
    }

    suspend fun createUser(name: String, email: String) {
        database.writeTransaction {
            database.execute(
                "INSERT INTO users (id, name, email) VALUES (uuid(), ?, ?)",
                listOf(name, email)
            )
        }
    }

    suspend fun deleteUser(id: String? = null) {
        val targetId =
            id ?: database.getOptional("SELECT id FROM users LIMIT 1", mapper = { cursor ->
                cursor.getString(0)!!
            })
            ?: return

        database.writeTransaction {
            database.execute("DELETE FROM users WHERE id = ?", listOf(targetId))
        }
    }
}