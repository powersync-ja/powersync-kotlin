package com.powersync.connector.supabase

import com.powersync.connectors.Authenticator
import com.powersync.db.runWrapped
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient

/**
 * Authenticates against PowerSync using the current Supabase session.
 * Supabase manages token persistence and automatic refresh through its Auth plugin.
 */
public open class SupabaseAuthenticator(
    public val supabaseClient: SupabaseClient,
) : Authenticator {
    public constructor(
        supabaseUrl: String,
        supabaseKey: String,
    ) : this(
        createSupabaseClient(supabaseUrl, supabaseKey) {
            install(Auth)
        },
    )

    init {
        require(supabaseClient.pluginManager.getPluginOrNull(Auth) != null) { "The Auth plugin must be installed on the Supabase client" }
    }

    /**
     * Get credentials for PowerSync.
     */
    override suspend fun resolveCredentials(): String =
        runWrapped {
            check(supabaseClient.auth.sessionStatus.value is SessionStatus.Authenticated) { "Supabase client is not authenticated" }

            // Use Supabase token for PowerSync
            val session =
                supabaseClient.auth.currentSessionOrNull()
                    ?: error("Could not fetch Supabase credentials")

            check(session.user != null) { "No user data" }
            session.accessToken
        }
}
