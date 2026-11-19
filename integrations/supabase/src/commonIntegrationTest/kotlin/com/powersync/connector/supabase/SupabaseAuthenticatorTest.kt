package com.powersync.connector.supabase

import com.powersync.PowerSyncException
import com.powersync.connectors.PowerSyncBackendConnector
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SupabaseAuthenticatorTest {
    @Test
    fun resolvesCurrentSessionWithoutPostgrest() =
        runTest {
            val client =
                createSupabaseClient("https://example.supabase.co", "test-key") {
                    install(Auth) {
                        autoLoadFromStorage = false
                        autoSaveToStorage = false
                        alwaysAutoRefresh = false
                    }
                }
            try {
                val authenticator = SupabaseAuthenticator(client)
                val error = assertFailsWith<PowerSyncException> { authenticator.resolveCredentials() }
                error.cause!!.message shouldBe "Supabase client is not authenticated"

                val session =
                    UserSession(
                        accessToken = "first-token",
                        refreshToken = "refresh-token",
                        expiresIn = 3600,
                        tokenType = "bearer",
                        user = UserInfo(aud = "authenticated", id = "user-id"),
                    )
                client.auth.importSession(session)
                authenticator.resolveCredentials() shouldBe "first-token"

                client.auth.importSession(session.copy(accessToken = "refreshed-token"))
                authenticator.resolveCredentials() shouldBe "refreshed-token"
            } finally {
                client.close()
            }
        }

    @Suppress("DEPRECATION")
    @Test
    fun legacyConnectorStillProvidesCredentialsAndCaching() =
        runTest {
            val client =
                createSupabaseClient("https://example.supabase.co", "test-key") {
                    install(Auth) {
                        autoLoadFromStorage = false
                        autoSaveToStorage = false
                        alwaysAutoRefresh = false
                    }
                    install(Postgrest)
                }
            try {
                client.auth.importSession(
                    UserSession(
                        accessToken = "legacy-token",
                        refreshToken = "refresh-token",
                        expiresIn = 3600,
                        tokenType = "bearer",
                        user = UserInfo(aud = "authenticated", id = "user-id"),
                    ),
                )
                val connector = SupabaseConnector(client, "https://example.powersync.com")
                val legacy: PowerSyncBackendConnector = connector
                connector.powerSyncEndpoint shouldBe "https://example.powersync.com"
                legacy.fetchCredentials()!!.endpoint shouldBe "https://example.powersync.com"
                legacy.getCredentialsCached()!!.token shouldBe "legacy-token"
                legacy.invalidateCredentials()
                legacy.getCredentialsCached()!!.token shouldBe "legacy-token"
            } finally {
                client.close()
            }
        }
}
