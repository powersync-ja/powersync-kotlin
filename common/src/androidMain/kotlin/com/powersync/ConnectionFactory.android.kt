package com.powersync

@ExperimentalPowerSyncAPI
@Throws(PowerSyncException::class)
public actual fun resolvePowerSyncLoadableExtensionPath(): String? = "libpowersync.so"
