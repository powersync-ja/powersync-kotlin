package com.powersync

import co.touchlab.kermit.Logger
import co.touchlab.skie.configuration.annotations.DefaultArgumentInterop
import com.powersync.db.ActiveDatabaseGroup
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.driver.InternalConnectionPool
import com.powersync.db.driver.LazyPool
import com.powersync.db.schema.Schema
import com.powersync.utils.generateLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

public const val DEFAULT_DB_FILENAME: String = "powersync.db"

/**
 * Use this to create a [PowerSyncDatabase]
 */
@OptIn(DelicateCoroutinesApi::class)
@DefaultArgumentInterop.Enabled
public fun PowerSyncDatabase(
    factory: PersistentConnectionFactory,
    schema: Schema,
    dbFilename: String = DEFAULT_DB_FILENAME,
    scope: CoroutineScope = GlobalScope,
    logger: Logger? = null,
    dispatchStrategy: DispatchStrategy = DispatchStrategy.Default,
    /**
     * Optional database file directory path.
     * This parameter is ignored for iOS.
     */
    dbDirectory: String? = null,
): PowerSyncDatabase {
    val generatedLogger: Logger = generateLogger(logger)

    return createPowerSyncDatabaseImpl(
        schema = schema,
        factory = factory,
        dbFilename = dbFilename,
        scope = scope,
        logger = generatedLogger,
        dbDirectory = dbDirectory,
        dispatchStrategy = dispatchStrategy,
    )
}

internal fun createPowerSyncDatabaseImpl(
    factory: PersistentConnectionFactory,
    schema: Schema,
    dbFilename: String,
    scope: CoroutineScope,
    logger: Logger,
    dbDirectory: String?,
    dispatchStrategy: DispatchStrategy = DispatchStrategy.Default,
): PowerSyncDatabaseImpl {
    val identifier = dbDirectory + dbFilename
    val activeDatabaseGroup = ActiveDatabaseGroup.referenceDatabase(logger, identifier)

    val pool =
        LazyPool {
            InternalConnectionPool(
                factory,
                scope,
                dbFilename,
                dbDirectory,
                activeDatabaseGroup.first.group.writeLockMutex,
            )
        }

    return PowerSyncDatabase.openedWithGroup(
        pool,
        scope,
        schema,
        logger,
        activeDatabaseGroup,
        dispatchStrategy,
    ) as PowerSyncDatabaseImpl
}
