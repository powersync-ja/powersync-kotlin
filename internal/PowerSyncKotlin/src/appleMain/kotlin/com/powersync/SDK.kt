// This is required to build the iOS framework

package com.powersync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.powersync.bucket.StreamPriority
import com.powersync.db.NativeConnectionFactory
import com.powersync.db.crud.CrudTransaction
import com.powersync.sync.SyncClientConfiguration
import com.powersync.sync.SyncOptions
import com.powersync.sync.SyncStatusData
import com.powersync.sync.SyncStream
import com.powersync.sync.SyncStreamDescription
import com.powersync.sync.SyncStreamStatus
import com.powersync.sync.SyncStreamSubscription
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.logging.Logger as KtorLogger

public fun sqlite3DatabaseFactory(initialStatements: List<String>): PersistentConnectionFactory {
    @OptIn(ExperimentalPowerSyncAPI::class)
    return object : NativeConnectionFactory() {
        override fun resolveDefaultDatabasePath(dbFilename: String): String = appleDefaultDatabasePath(dbFilename)

        override fun openConnection(
            path: String,
            openFlags: Int,
        ): SQLiteConnection {
            val conn = super.openConnection(path, openFlags)
            try {
                for (statement in initialStatements) {
                    conn.execSQL(statement)
                }
            } catch (e: PowerSyncException) {
                conn.close()
                throw e
            }

            return super.openConnection(path, openFlags)
        }
    }
}

/**
 * Helper class designed to bridge SKIEE methods and allow them to throw
 * `PowerSyncException`. This is necessary because these exceptions cannot
 * be thrown directly in Swift.
 *
 * The class provides a mechanism to handle exceptions in a way that is
 * compatible with the Swift environment, ensuring proper error propagation
 * and handling.
 */
@Throws(PowerSyncException::class)
public fun throwPowerSyncException(exception: PowerSyncException): Unit = throw exception

/**
 * A small wrapper around the Ktor LogLevel enum to allow
 * specifying the log level from Swift without exposing the Ktor plugin types.
 */
public enum class SwiftSyncRequestLogLevel {
    ALL,
    HEADERS,
    BODY,
    INFO,
    NONE,
}

/**
 * Mapper function to Ktor LogLevel
 */
internal fun SwiftSyncRequestLogLevel.toKtorLogLevel(): LogLevel =
    when (this) {
        SwiftSyncRequestLogLevel.ALL -> LogLevel.ALL
        SwiftSyncRequestLogLevel.HEADERS -> LogLevel.HEADERS
        SwiftSyncRequestLogLevel.BODY -> LogLevel.BODY
        SwiftSyncRequestLogLevel.INFO -> LogLevel.INFO
        SwiftSyncRequestLogLevel.NONE -> LogLevel.NONE
    }

/**
 * Configuration which is used to configure the Ktor logging plugin
 */
public data class SwiftRequestLoggerConfig(
    public val logLevel: SwiftSyncRequestLogLevel,
    public val log: (message: String) -> Unit,
)

/**
 * Creates a Ktor [SyncClientConfiguration.ExtendedConfig] that extends the default Ktor client.
 * Specifying a [SwiftRequestLoggerConfig] will install the Ktor logging plugin with the specified configuration.
 */
public fun createExtendedSyncClientConfiguration(loggingConfig: SwiftRequestLoggerConfig? = null): SyncClientConfiguration =
    SyncClientConfiguration.ExtendedConfig {
        if (loggingConfig != null) {
            install(Logging) {
                // Pass everything to the provided logger. The logger controls the active level
                level = loggingConfig.logLevel.toKtorLogLevel()
                logger =
                    object : KtorLogger {
                        override fun log(message: String) {
                            loggingConfig.log(message)
                        }
                    }
            }
        }
    }

/**
 * Creates a [SyncOptions] based on simple parameters, because creating the actual instance with
 * the default constructor is not possible from Swift due to an optional argument with an internal
 * default value.
 */
@OptIn(ExperimentalPowerSyncAPI::class)
public fun createSyncOptions(
    newClient: Boolean,
    userAgent: String,
    loggingConfig: SwiftRequestLoggerConfig? = null,
): SyncOptions =
    SyncOptions(
        newClientImplementation = newClient,
        userAgent = userAgent,
        clientConfiguration = createExtendedSyncClientConfiguration(loggingConfig),
    )

public fun errorHandledCrudTransactions(db: PowerSyncDatabase): Flow<PowerSyncResult> =
    db
        .getCrudTransactions()
        .map<CrudTransaction, PowerSyncResult> {
            PowerSyncResult.Success(it)
        }.catch {
            if (it is PowerSyncException) {
                emit(PowerSyncResult.Failure(it))
            } else {
                throw it
            }
        }

/**
 * Calls [SyncStream.subscribe] with types that are more convenient to construct in Swift:
 *
 * The `ttl` uses a [Double] representing seconds, `priority` is represented as the priority number
 * instead of the [StreamPriority].
 */
@Throws(PowerSyncException::class, CancellationException::class)
public suspend fun syncStreamSubscribeSwift(stream: SyncStream, ttl: Double?, priority: Int?): SyncStreamSubscription {
    return stream.subscribe(
        ttl = ttl?.seconds,
        priority = priority?.let { StreamPriority(it) },
    )
}

public fun syncStatusForStream(status: SyncStatusData, name: String, parameters: Map<String, Any?>?): SyncStreamStatus? {
    return status.forStream(object: SyncStreamDescription {
        override val name: String
            get() = name
        override val parameters: Map<String, Any?>?
            get() = parameters
    })
}
