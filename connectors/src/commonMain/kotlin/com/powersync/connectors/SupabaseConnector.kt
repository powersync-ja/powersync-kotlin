package com.powersync.connectors

import com.powersync.db.PowerSyncDatabase
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.UpdateType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking

class SupabaseConnector : com.powersync.connectors.PowerSyncBackendConnector() {

    companion object {
        // TODO this needs to be provided by the user/dev
        private const val POWERSYNC_URL =
            "https://65a0e6bb4078d9a211d3cffb.powersync.journeyapps.com"
        private const val SUPABASE_URL = "https://wtilkjczshmzekrjelco.supabase.co"
        private const val SUPABASE_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Ind0aWxramN6c2htemVrcmplbGNvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MDUwNDM2MTYsImV4cCI6MjAyMDYxOTYxNn0.E4DWa1ftn92_rQP-aLTsQHsZufouhMmzBfsCiX2p5eM"

        private const val TEST_EMAIL = "hello@powersync.com";
        private const val TEST_PASSWORD = "@dYX0}72eS0kT=(YG@8(";
    }

    private val supabaseClient: SupabaseClient;

    init {
        supabaseClient = createClient()

        runBlocking {
            login()
            val creds = fetchCredentials()
            println("Creds $creds")
        }
    }

    private fun createClient(): SupabaseClient {
        val client = createSupabaseClient(
            supabaseUrl = com.powersync.connectors.SupabaseConnector.Companion.SUPABASE_URL,
            supabaseKey = com.powersync.connectors.SupabaseConnector.Companion.SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }

        return client
    }

    private suspend fun login(): Unit {
        val res = supabaseClient.auth.signInWith(Email) {
            email = com.powersync.connectors.SupabaseConnector.Companion.TEST_EMAIL
            password = com.powersync.connectors.SupabaseConnector.Companion.TEST_PASSWORD
        }

        return res;
    }

    override suspend fun fetchCredentials(): com.powersync.connectors.PowerSyncCredentials {
        val session = supabaseClient.auth.currentSessionOrNull()
            ?: throw Exception("Could not fetch Supabase credentials");

        if (session.user == null) {
            throw Exception("No user data")
        }

        return com.powersync.connectors.PowerSyncCredentials(
            endpoint = com.powersync.connectors.SupabaseConnector.Companion.POWERSYNC_URL,
            token = session.accessToken,
            expiresAt = session.expiresAt,
            userId = session.user!!.id
        );
    }

    /**
     * Upload pending changes to Supabase.
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
            // TODO add retry logic
            println("Data upload error - discarding ${lastEntry!!}, $e")
            throw e;
        }
    }
}