package com.powersync.connector.supabase

import co.touchlab.kermit.Logger
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.UpdateType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.StateFlow

/**
 * Get a Supabase token to authenticate against the PowerSync instance.
 */
public class SupabaseConnector(
    public val supabaseClient: SupabaseClient,
    public val powerSyncEndpoint: String,
) : PowerSyncBackendConnector() {
    public constructor(
        supabaseUrl: String,
        supabaseKey: String,
        powerSyncEndpoint: String,
    ) : this(
        supabaseClient =
            createSupabaseClient(supabaseUrl, supabaseKey) {
                install(Auth)
                install(Postgrest)
            },
        powerSyncEndpoint = powerSyncEndpoint,
    )

    init {
        require(supabaseClient.pluginManager.getPluginOrNull(Auth) != null) { "The Auth plugin must be installed on the Supabase client" }
        require(
            supabaseClient.pluginManager.getPluginOrNull(Postgrest) != null,
        ) { "The Postgrest plugin must be installed on the Supabase client" }
    }

    public suspend fun login(
        email: String,
        password: String,
    ) {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    public suspend fun signUp(
        email: String,
        password: String,
    ) {
        supabaseClient.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    public suspend fun signOut() {
        supabaseClient.auth.signOut()
    }

    public fun session(): UserSession? = supabaseClient.auth.currentSessionOrNull()

    public val sessionStatus: StateFlow<SessionStatus> = supabaseClient.auth.sessionStatus

    public suspend fun loginAnonymously() {
        supabaseClient.auth.signInAnonymously()
    }

    /**
     * Get credentials for PowerSync.
     */
    override suspend fun fetchCredentials(): PowerSyncCredentials {
        check(supabaseClient.auth.sessionStatus.value is SessionStatus.Authenticated) { "Supabase client is not authenticated" }

        // Use Supabase token for PowerSync
        val session = supabaseClient.auth.currentSessionOrNull() ?: error("Could not fetch Supabase credentials")

        check(session.user != null) { "No user data" }

        // userId is for debugging purposes only
        return PowerSyncCredentials(
            endpoint = powerSyncEndpoint,
            token = session.accessToken, // Use the access token to authenticate against PowerSync
            userId = session.user!!.id,
        )
    }

    /**
     * Upload local changes to the app backend (in this case Supabase).
     *
     * This function is called whenever there is data to upload, whether the device is online or offline.
     * If this call throws an error, it is retried periodically.
     */
    override suspend fun uploadData(database: PowerSyncDatabase) {
        val transaction = database.getNextCrudTransaction() ?: return

        var lastEntry: CrudEntry? = null
        try {
            for (entry in transaction.crud) {
                lastEntry = entry

                val table = supabaseClient.from(entry.table)
                when (entry.op) {
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
            }

            transaction.complete(null)
        } catch (e: Exception) {
            when (e) {
                is BadRequestRestException -> {
                    if (e.message?.contains("violates not-null constraint") == true) {
                        Logger.e("Not-null constraint violation: ${e.message}")
                        transaction.complete(null)
                        return
                    }
                }
                else -> {
                    Logger.e("Data upload error - retrying last entry: $lastEntry, $e")
                    throw e
                }
            }
        }
    }
}
