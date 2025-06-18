package com.powersync

import co.touchlab.sqliter.DatabaseConnection

internal actual fun DatabaseConnection.loadPowerSyncSqliteCoreExtension() {
    loadPowerSyncSqliteCoreExtensionDynamically()
}
