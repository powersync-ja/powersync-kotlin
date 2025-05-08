package com.powersync.bucket

import com.powersync.sync.LegacySyncImplementation

@LegacySyncImplementation
internal data class LocalOperationCounters(
    val atLast: Int,
    val sinceLast: Int,
)
