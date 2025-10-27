package com.powersync

@Throws(PowerSyncException::class)
public actual fun resolvePowerSyncLoadableExtensionPath(): String? = "libpowersync.so"
