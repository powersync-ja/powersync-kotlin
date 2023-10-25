package co.powersync.kotlin

import io.github.jan.supabase.SupabaseClient
import kotlinx.datetime.Instant

class PowerSyncCredentials(val client: SupabaseClient, val endpoint: String, val token: String, val expiresAt: Instant, val userID: String)

abstract class PowerSyncBackendConnector {
    /// Get credentials for PowerSync.
    ///
    /// This should always fetch a fresh set of credentials - don't use cached
    /// values.
    ///
    /// Return null if the user is not signed in. Throw an error if credentials
    /// cannot be fetched due to a network error or other temporary error.
    ///
    /// This token is kept for the duration of a sync connection.
    abstract suspend fun fetchCredentials(): PowerSyncCredentials;

    /// Upload local changes to the app backend.
    ///
    /// Use [PowerSyncDatabase.getCrudBatch] to get a batch of changes to upload. See [DevConnector] for an example implementation.
    ///
    /// Any thrown errors will result in a retry after the configured wait period (default: 5 seconds).
    abstract suspend fun uploadData(database: AbstractPowerSyncDatabase);
}