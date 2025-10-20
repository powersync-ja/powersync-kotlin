package com.powersync

import co.touchlab.kermit.Logger
import com.powersync.db.schema.Schema
import kotlinx.coroutines.CoroutineScope

/**
 * Creates an in-memory PowerSync database instance, useful for testing.
 */
@OptIn(ExperimentalPowerSyncAPI::class)
public fun PowerSyncDatabase.PowerSyncOpenFactory.inMemory(
    schema: Schema,
    scope: CoroutineScope,
    logger: Logger? = null,
): PowerSyncDatabase =
    openInMemory(
        factory = inMemoryDriver,
        schema = schema,
        scope = scope,
        logger = logger,
    )
