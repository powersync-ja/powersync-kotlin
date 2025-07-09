package com.powersync.connectors

import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.db.runWrapped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Implement this to connect an app backend.
 *
 * The connector is responsible for:
 * 1. Creating credentials for connecting to the PowerSync service.
 * 2. Applying local changes against the backend application server.
 *
 */
public abstract class PowerSyncBackendConnector {
    private var cachedCredentials: PowerSyncCredentials? = null
    private var fetchRequest: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Get credentials current cached, or fetch new credentials if none are
     * available.
     *
     * These credentials may have expired already.
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public open suspend fun getCredentialsCached(): PowerSyncCredentials? {
        return runWrapped {
            cachedCredentials?.let { return@runWrapped it }
            prefetchCredentials().join()
            cachedCredentials
        }
    }

    /**
     * Immediately invalidate credentials.
     *
     * This may be called when the current credentials have expired.
     */
    public open fun invalidateCredentials() {
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
    @Throws(PowerSyncException::class, CancellationException::class)
    public open fun prefetchCredentials(): Job {
        fetchRequest?.takeIf { it.isActive }?.let { return it }

        val request =
            scope.launch {
                fetchCredentials().also { value ->
                    cachedCredentials = value
                    fetchRequest = null
                }
            }

        fetchRequest = request
        return request
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
    @Throws(PowerSyncException::class, CancellationException::class)
    public abstract suspend fun fetchCredentials(): PowerSyncCredentials?

    /**
     * Upload local changes to the app backend.
     *
     * Use [PowerSyncDatabase.getCrudBatch] to get a batch of changes to upload.
     *
     * Any thrown errors will result in a retry after the configured wait period (default: 5 seconds).
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public abstract suspend fun uploadData(database: PowerSyncDatabase)
}
