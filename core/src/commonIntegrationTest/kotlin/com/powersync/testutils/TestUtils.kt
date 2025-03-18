package com.powersync.testutils

import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials

expect val factory: DatabaseDriverFactory

expect fun cleanup(path: String)

