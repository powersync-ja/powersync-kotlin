package com.powersync

import co.touchlab.kermit.Logger
import com.powersync.db.ActiveDatabaseGroup
import com.powersync.db.driver.SingleConnectionPool
import com.powersync.db.schema.Schema
import com.powersync.utils.generateLogger
import kotlinx.coroutines.CoroutineScope

/**
 * Creates a PowerSync database backed by a single in-memory database connection opened from the
 * [InMemoryConnectionFactory].
 *
 * This can be useful for writing tests relying on PowerSync databases.
 */
public fun PowerSyncDatabase.PowerSyncOpenFactory.openInMemory(
    factory: InMemoryConnectionFactory,
    schema: Schema,
    scope: CoroutineScope,
    logger: Logger? = null,
): PowerSyncDatabase {
    val logger = generateLogger(logger)
    // Since this returns a fresh in-memory database every time, use a fresh group to avoid warnings about the
    // same database being opened multiple times.
    val collection =
        ActiveDatabaseGroup.GroupsCollection().referenceDatabase(logger, "test")

    return openedWithGroup(
        SingleConnectionPool(factory.openInMemoryConnection()),
        scope,
        schema,
        logger,
        collection,
    )
}
