package com.powersync.connectors

import com.powersync.PowerSyncDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

/**
 * Implement this to connect an app backend.
 *
 * The connector is responsible for:
 * 1. Creating credentials for connecting to the PowerSync service.
 * 2. Applying local changes against the backend application server.
 *
 */
abstract class PowerSyncBackendConnector {
    private var cachedCredentials: PowerSyncCredentials? = null
    private var fetchRequest: Deferred<PowerSyncCredentials?>? = null

    /**
     * Get credentials current cached, or fetch new credentials if none are
     * available.
     *
     * These credentials may have expired already.
     */
    suspend fun getCredentialsCached(): PowerSyncCredentials? {
        cachedCredentials?.let { return it }
        return prefetchCredentials()
    }

    /**
     * Immediately invalidate credentials.
     *
     * This may be called when the current credentials have expired.
     */
    fun invalidateCredentials() {
        cachedCredentials = null
    }

    /**
     * Fetch a new set of credentials and cache it.
     *
     * Until this call succeeds, [getCredentialsCached] will still return the
     * old credentials.
     *
     * This may be called before the current credentials have expired.
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun prefetchCredentials(): PowerSyncCredentials? {
        fetchRequest = fetchRequest ?: GlobalScope.async {
            fetchCredentials().also { value ->
                cachedCredentials = value
            }
        }

        return fetchRequest?.await()
    }

    /**
     * Get credentials for PowerSync.
     *
     * This should always fetch a fresh set of credentials - don't use cached
     * values.
     *
     * Return null if the user is not signed in. Throw an error if credentials
     * cannot be fetched due to a network error or other temporary error.
     *
     * This token is kept for the duration of a sync connection.
     */
    abstract suspend fun fetchCredentials(): PowerSyncCredentials?

    /**
     * Upload local changes to the app backend.
     *
     * Use [PowerSyncDatabase.getCrudBatch] to get a batch of changes to upload.
     *
     * Any thrown errors will result in a retry after the configured wait period (default: 5 seconds).
     */
    abstract suspend fun uploadData(database: PowerSyncDatabase)
}