package com.powersync.demos

import com.powersync.DatabaseDriverFactory
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import com.powersync.db.getString
import com.powersync.sync.SyncClientConfiguration
import com.powersync.sync.SyncOptions
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

class PowerSync(
    driverFactory: DatabaseDriverFactory,
) {
    private val connector =
        SupabaseConnector(
            supabaseUrl = Config.SUPABASE_URL,
            supabaseKey = Config.SUPABASE_ANON_KEY,
            powerSyncEndpoint = Config.POWERSYNC_URL,
        )
    private val database = PowerSyncDatabase(driverFactory, AppSchema)

    val db: PowerSyncDatabase
        get() = database

    init {
        runBlocking {
            try {
                connector.login(Config.SUPABASE_USER_EMAIL, Config.SUPABASE_USER_PASSWORD)
            } catch (e: Exception) {
                println(
                    "Could not connect to Supabase, have you configured an auth user and set `SUPABASE_USER_EMAIL` and `SUPABASE_USER_PASSWORD`?\n Error: $e",
                )
            }
            database.connect(connector)
        }
    }

    suspend fun getPowersyncVersion(): String = database.getPowerSyncVersion()

    fun watchUsers(): Flow<List<User>> =
        database.watch("SELECT * FROM customers", mapper = { cursor ->
            User(
                id = cursor.getString("id"),
                name = cursor.getString("name"),
                email = cursor.getString("email"),
            )
        })

    suspend fun createUser(
        name: String,
        email: String,
    ) {
        database.writeTransaction { tx ->
            tx.execute(
                "INSERT INTO customers (id, name, email) VALUES (uuid(), ?, ?)",
                listOf(name, email),
            )
        }
    }

    suspend fun deleteUser(id: String? = null) {
        val targetId =
            id ?: database.getOptional("SELECT id FROM customers LIMIT 1", mapper = { cursor ->
                cursor.getString(0)!!
            })
            ?: return

        database.writeTransaction { tx ->
            tx.execute("DELETE FROM customers WHERE id = ?", listOf(targetId))
        }
    }

    @OptIn(ExperimentalPowerSyncAPI::class)
    suspend fun connect() {
        println("connecting to PowerSync...")
        database.connect(
            connector,
            options =
                SyncOptions(
                    clientConfiguration = SyncClientConfiguration.ExtendedConfig {
                        install(Logging) {
                            level = LogLevel.ALL
                        }
                    }
                ),
        )
    }

    suspend fun disconnect() {
        database.disconnect()
    }
}
