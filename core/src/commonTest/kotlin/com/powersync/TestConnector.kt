package com.powersync

import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials

class TestConnector : PowerSyncBackendConnector() {
    var fetchCredentialsCallback: suspend () -> PowerSyncCredentials? = {
        PowerSyncCredentials(
            token = "test-token",
            userId = "test-user",
            endpoint = "https://test.com",
        )
    }
    var uploadDataCallback: suspend (PowerSyncDatabase) -> Unit = {
        val tx = it.getNextCrudTransaction()
        tx?.complete(null)
    }

    override suspend fun fetchCredentials(): PowerSyncCredentials? = fetchCredentialsCallback()

    override suspend fun uploadData(database: PowerSyncDatabase) {
        uploadDataCallback(database)
    }
}
