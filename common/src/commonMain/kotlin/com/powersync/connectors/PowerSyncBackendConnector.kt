package com.powersync.connectors

import com.powersync.ExperimentalCheckpointRequestsApi
import com.powersync.ExperimentalPowerSyncAPI
import com.powersync.PowerSyncDatabase
import com.powersync.PowerSyncException
import com.powersync.db.runWrapped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    internal var cachedCredentials: PowerSyncCredentials? = null
    private var fetchingCredentials = Mutex()

    private var fetchRequest: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private suspend fun fetchAndCacheCredentials(): PowerSyncCredentials? =
        fetchCredentials().also {
            cachedCredentials = it
        }

    /**
     * Get credentials current cached, or fetch new credentials if none are
     * available.
     *
     * These credentials may have expired already.
     */
    public open suspend fun getCredentialsCached(): PowerSyncCredentials? {
        return runWrapped {
            cachedCredentials?.let { return@runWrapped it }

            return fetchingCredentials.withLock {
                // With concurrent calls, it's possible that credentials have just been fetched.
                cachedCredentials?.let { return it }

                val credentials = fetchAndCacheCredentials()
                check(credentials === cachedCredentials)
                credentials
            }
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
    @Deprecated(
        "Call updateCredentials, bring your own CoroutineScope if you need it to be asynchronous",
        replaceWith = ReplaceWith("updateCredentials"),
    )
    public open fun prefetchCredentials(): Job {
        fetchRequest?.takeIf { it.isActive }?.let { return it }

        val request =
            scope.launch {
                fetchAndCacheCredentials().also { fetchRequest = null }
            }

        fetchRequest = request
        return request
    }

    /**
     * If no other task is currently fetching credentials, calls [fetchCredentials] again and caches
     * the result internally.
     *
     * This is used by the sync client if a token is about to expire: By fetching a new token early,
     * we can avoid interruptions in the sync process.
     */
    public suspend fun updateCredentials() {
        if (fetchingCredentials.tryLock()) {
            try {
                fetchAndCacheCredentials()
            } finally {
                fetchingCredentials.unlock()
            }
        }
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
    public abstract suspend fun fetchCredentials(): PowerSyncCredentials?

    /**
     * Upload local changes to the app backend.
     *
     * Use [PowerSyncDatabase.getCrudBatch] to get a batch of changes to upload.
     *
     * Any thrown errors will result in a retry after the configured wait period (default: 5 seconds).
     */
    public abstract suspend fun uploadData(database: PowerSyncDatabase)
}

/**
 * A [PowerSyncBackendConnector] capable of requesting checkpoints.
 *
 * Extend this class instead of [PowerSyncBackendConnector] when uploads are processed
 * asynchronously by the backend (for example through a message queue): The sync client as part of
 * the PowerSync Kotlin SDK generates a checkpoint request id and hands it to your backend via this
 * class, which is responsible for creating a matching checkpoint once the uploads preceding the
 * request have been processed.
 * For more details, see [asynchronous backend uploads](https://docs.powersync.com/client-sdks/advanced/checkpoint-requests#asynchronous-upload-backends).
 *
 * To use this connector, using [com.powersync.sync.CheckpointMode.Requests] is required. Note that
 * this requires PowerSync service version 1.24.0 or later.
 */
@ExperimentalCheckpointRequestsApi
public abstract class CustomCheckpointRequestConnector : PowerSyncBackendConnector() {
    /**
     * Posts a client-generated checkpoint request to the backend and returns the effective
     * checkpoint request state.
     *
     * @param clientId The PowerSync client ID for the current device.
     * @param requestId The client-generated checkpoint request ID.
     */
    public abstract suspend fun postCheckpointRequest(
        clientId: String,
        requestId: Long,
    ): Long
}

// Not using this indirection causes linker errors in tests: https://youtrack.jetbrains.com/issue/CMP-3318
internal fun PowerSyncBackendConnector.readCachedCredentials(): PowerSyncCredentials? = this.cachedCredentials
