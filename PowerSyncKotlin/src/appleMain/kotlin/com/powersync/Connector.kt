package com.powersync

import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials

public interface SwiftBackendConnector {
    public suspend fun fetchCredentials(): PowerSyncResult
    public suspend fun uploadData(): PowerSyncResult
}

public fun swiftBackendConnectorToPowerSyncConnector(connector: SwiftBackendConnector): PowerSyncBackendConnector {
    return object : PowerSyncBackendConnector() {
        override suspend fun fetchCredentials(): PowerSyncCredentials? {
            return handleLockResult(connector.fetchCredentials()) as PowerSyncCredentials?
        }

        override suspend fun uploadData(database: PowerSyncDatabase) {
            handleLockResult(connector.uploadData())
        }
    }
}
