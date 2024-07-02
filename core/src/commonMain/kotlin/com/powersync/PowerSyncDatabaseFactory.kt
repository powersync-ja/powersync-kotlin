package com.powersync

import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Schema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import co.touchlab.skie.configuration.annotations.DefaultArgumentInterop
import co.touchlab.kermit.Logger
import com.powersync.utils.generateLogger

public const val DEFAULT_DB_FILENAME: String = "powersync.db"

/**
 * Use this to create a [PowerSyncDatabase]
 */
@OptIn(DelicateCoroutinesApi::class)
@DefaultArgumentInterop.Enabled
public fun PowerSyncDatabase(
    factory: DatabaseDriverFactory,
    schema: Schema,
    dbFilename: String = DEFAULT_DB_FILENAME,
    scope: CoroutineScope = GlobalScope,
    logger: Logger? = null
): PowerSyncDatabase {
    val generatedLogger: Logger = generateLogger(logger)

    return PowerSyncDatabaseImpl(
        schema = schema,
        factory = factory,
        dbFilename = dbFilename,
        scope = scope,
        logger = generatedLogger
    )
}