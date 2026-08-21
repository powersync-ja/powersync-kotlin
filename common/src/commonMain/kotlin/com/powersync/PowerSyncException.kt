package com.powersync

public open class PowerSyncException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause)
