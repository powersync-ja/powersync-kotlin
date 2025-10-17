package com.powersync

import co.touchlab.kermit.Logger
import com.powersync.db.ActiveDatabaseGroup
import com.powersync.db.driver.SingleConnectionPool
import com.powersync.db.schema.Schema
import com.powersync.utils.generateLogger
import kotlinx.coroutines.CoroutineScope

/**
 * Creates an in-memory PowerSync database instance, useful for testing.
 */
@OptIn(ExperimentalPowerSyncAPI::class)
public fun PowerSyncDatabase.PowerSyncOpenFactory.inMemory(
    schema: Schema,
    scope: CoroutineScope,
    logger: Logger? = null,
): PowerSyncDatabase {
    val logger = generateLogger(logger)
    // Since this returns a fresh in-memory database every time, use a fresh group to avoid warnings about the
    // same database being opened multiple times.
    val collection = ActiveDatabaseGroup.GroupsCollection().referenceDatabase(logger, "test")

    return openedWithGroup(
        SingleConnectionPool(openInMemoryConnection()),
        scope,
        schema,
        logger,
        collection,
    )
}
