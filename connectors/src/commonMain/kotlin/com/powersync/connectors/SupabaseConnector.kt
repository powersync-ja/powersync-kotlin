package com.powersync.connectors

import com.powersync.PowerSyncDatabase
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.UpdateType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from

<<<<<<< HEAD
/**
 * A backend connector tailored to Supabase backends.
 * The backend connector provides the connection between your application backend and the PowerSync managed database. The connector is responsible for:
 * 1. Obtaining credentials for connecting to the PowerSync service.
 * 2. Applying local changes against the backend application server.
=======
/** 
 * Get a Supabase token to authenticate against the PowerSync instance.
>>>>>>> 546550926577a8c171171bbe2214b68bea7113f7
 */
class SupabaseConnector(
    val supabaseUrl: String,
    val supabaseKey: String,
    val powerSyncEndpoint: String,
) : PowerSyncBackendConnector() {

    private var loggedIn: Boolean = false
    private val supabaseClient: SupabaseClient = createClient()

    private fun createClient(): SupabaseClient {
        val client = createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey
        ) {
            install(Auth)
            install(Postgrest)
        }

        return client
    }

    suspend fun login(email: String, password: String) {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        this.loggedIn = true
        fetchCredentials()
    }

    /**
     * Get credentials for PowerSync.
     */
    override suspend fun fetchCredentials(): PowerSyncCredentials {
        if (!loggedIn) {
            throw Exception("Not logged in")
        }

        // Use Supabase token for PowerSync
        val session = supabaseClient.auth.currentSessionOrNull()
            ?: throw Exception("Could not fetch Supabase credentials");

        if (session.user == null) {
            throw Exception("No user data")
        }

        // userId and expiresAt are for debugging purposes only
        return PowerSyncCredentials(
            endpoint = powerSyncEndpoint,
            token = session.accessToken, // Use the access token to authenticate against PowerSync
            expiresAt = session.expiresAt,
            userId = session.user!!.id
        );
    }

    /**
     * Upload local changes to the app backend (in this case Supabase).
     *
     * This function is called whenever there is data to upload, whether the device is online or offline.
     * If this call throws an error, it is retried periodically.
     */
    override suspend fun uploadData(database: PowerSyncDatabase) {

        val transaction = database.getNextCrudTransaction() ?: return;

        var lastEntry: CrudEntry? = null;
        try {

            for (entry in transaction.crud) {
                lastEntry = entry;

                val table = supabaseClient.from(entry.table)
                val result = when (entry.op) {
                    UpdateType.PUT -> {
                        val data = entry.opData?.toMutableMap() ?: mutableMapOf()
                        data["id"] = entry.id
                        table.upsert(data)
                    }

                    UpdateType.PATCH -> {
                        table.update(entry.opData!!) {
                            filter {
                                eq("id", entry.id)
                            }
                        }
                    }

                    UpdateType.DELETE -> {
                        table.delete {
                            filter {
                                eq("id", entry.id)
                            }
                        }
                    }
                }

                println("[SupabaseConnector::uploadData] $result")
            }

            transaction.complete(null);

        } catch (e: Exception) {
            // TODO implement discard logic
            println("Data upload error - discarding ${lastEntry!!}, $e")
            throw e
        }
    }
}