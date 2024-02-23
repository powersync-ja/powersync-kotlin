package com.powersync.demos

import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncBuilder
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.SupabaseConnector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

class PowerSync(
    driverFactory: DatabaseDriverFactory,
) {

    private val connector = SupabaseConnector(
        supabaseUrl = Config.SUPABASE_URL,
        supabaseKey = Config.SUPABASE_ANON_KEY,
        powerSyncEndpoint = Config.POWERSYNC_URL
    )
    private val database: PowerSyncDatabase =
        PowerSyncBuilder.from(driverFactory, AppSchema).build();

    init {
        runBlocking {
            connector.login(Config.SUPABASE_USER_EMAIL, Config.SUPABASE_USER_PASSWORD)
            database.connect(connector)
        }
    }

    suspend fun getPowersyncVersion(): String {
        return database.getPowerSyncVersion()
    }

    fun watchUsers(): Flow<List<User>> {
        return database.watch("SELECT * FROM customers", mapper = { cursor ->
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
                "INSERT INTO customers (id, name, email) VALUES (uuid(), ?, ?)",
                listOf(name, email)
            )
        }
    }

    suspend fun deleteUser(id: String? = null) {
        val targetId =
            id ?: database.getOptional("SELECT id FROM customers LIMIT 1", mapper = { cursor ->
                cursor.getString(0)!!
            })
            ?: return

        database.writeTransaction {
            database.execute("DELETE FROM customers WHERE id = ?", listOf(targetId))
        }
    }
}