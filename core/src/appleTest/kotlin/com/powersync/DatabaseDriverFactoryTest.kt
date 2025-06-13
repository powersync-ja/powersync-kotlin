package com.powersync

import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test

class DatabaseDriverFactoryTest {
    @OptIn(ExperimentalNativeApi::class)
    @Test
    fun findsPowerSyncFramework() {
        if (Platform.osFamily != OsFamily.WATCHOS) {
            // On watchOS targets, there's no special extension path because we expect to link the
            // PowerSync extension statically due to platform restrictions.
            DatabaseDriverFactory.powerSyncExtensionPath
        }
    }
}
