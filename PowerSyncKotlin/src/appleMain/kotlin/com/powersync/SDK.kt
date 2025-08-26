// This is required to build the iOS framework

package com.powersync

import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.sync.SyncClientConfiguration
import com.powersync.sync.SyncOptions
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.CancellationException
import io.ktor.client.plugins.logging.Logger as KtorLogger

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

/**
 * Swift 6 Strict concurrency checking causes an error if our adapter tries to use the non-sendable [PowerSyncDatabase] in [uploadData].
 * A small wrapper class which avoids the Swift SDK's Kotlin adapter from having to override the [uploadData] method.
 * In Swift we provide a Swift wrapper of the `database` which is used by the Swift connector's [uploadData] method.
 * We keep track of this adapter and provide it to the Swift handler automatically.
 * The Swift adapter will override the [performUpload] method to perform the actual upload.
 */
public abstract class SwiftPowerSyncBackendConnector : PowerSyncBackendConnector() {
    /**
     * We don't want to use the [PowerSyncDatabase] here since we can't declare it
     * as Sendable. Swift6 will complain.
     * We currently adapt the database and provide a wrapped version of the Swift
     * database there.
     */
    public override suspend fun uploadData(database: PowerSyncDatabase): Unit = performUpload()

    /**
     * This will be used to perform the actual uploads of the data
     */
    @Throws(PowerSyncException::class, CancellationException::class)
    public abstract suspend fun performUpload(): Unit
}
