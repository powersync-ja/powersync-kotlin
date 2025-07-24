package com.powersync

import com.powersync.internal.driver.NativeConnection

internal actual fun NativeConnection.loadPowerSyncSqliteCoreExtension() {
    loadPowerSyncSqliteCoreExtensionDynamically()
}
