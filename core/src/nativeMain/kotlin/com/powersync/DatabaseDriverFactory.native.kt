package com.powersync

import com.powersync.internal.driver.NativeDriver
import com.powersync.internal.driver.PowerSyncDriver

public actual val RawDatabaseFactory: PowerSyncDriver = NativeDriver()
