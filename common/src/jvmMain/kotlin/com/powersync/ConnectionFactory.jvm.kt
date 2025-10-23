package com.powersync

import com.powersync.db.runWrapped

@ExperimentalPowerSyncAPI
@Throws(PowerSyncException::class)
public actual fun resolvePowerSyncLoadableExtensionPath(): String? = runWrapped { powersyncExtension }

private val powersyncExtension: String by lazy { extractLib("powersync") }
