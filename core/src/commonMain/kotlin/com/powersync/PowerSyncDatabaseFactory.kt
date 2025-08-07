package com.powersync

import BuildConfig
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.StaticConfig
import co.touchlab.kermit.platformLogWriter
import co.touchlab.skie.configuration.annotations.DefaultArgumentInterop
import com.powersync.db.PowerSyncDatabaseImpl
import com.powersync.db.schema.Schema
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
    factory: DatabaseDriverFactory,
    schema: Schema,
    dbFilename: String = DEFAULT_DB_FILENAME,
    scope: CoroutineScope = GlobalScope,
    logger: Logger? = null,
    /**
     * Optional database file directory path.
     * This parameter is ignored for iOS.
     */
    dbDirectory: String? = null,
): PowerSyncDatabase =
    createPowerSyncDatabaseImpl(
        schema = schema,
        factory = factory,
        dbFilename = dbFilename,
        scope = scope,
        logger =
        logger
            ?: Logger(
                config = StaticConfig(
                    logWriterList =
                    listOf(platformLogWriter()),
                    minSeverity =
                    if (BuildConfig.isDebug)
                        Severity.Verbose
                    else
                        Severity.Warn,
                ),
                tag = "PowerSync",
            ),
        dbDirectory = dbDirectory,
    )

internal fun createPowerSyncDatabaseImpl(
    factory: DatabaseDriverFactory,
    schema: Schema,
    dbFilename: String,
    scope: CoroutineScope,
    logger: Logger,
    dbDirectory: String?,
): PowerSyncDatabaseImpl =
    PowerSyncDatabaseImpl(
        schema = schema,
        factory = factory,
        dbFilename = dbFilename,
        scope = scope,
        logger = logger,
        dbDirectory = dbDirectory,
    )
