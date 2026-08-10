package com.powersync.utils

internal actual fun maybeSharedMutex(name: String): PowerSyncMutex = localMutex()
