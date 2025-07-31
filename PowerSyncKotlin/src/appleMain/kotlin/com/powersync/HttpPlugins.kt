package com.powersync

import com.powersync.sync.SyncClientConfiguration
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger as KtorLogger

/**
 * A small wrapper around the Ktor LogLevel enum to allow
 * specifying the log level from Swift without exposing the Ktor plugin types.
 */
public enum class SwiftNetworkLogLevel {
    ALL,
    HEADERS,
    BODY,
    INFO,
    NONE,
}

/**
 * Mapper function to Ktor LogLevel
 */
internal fun SwiftNetworkLogLevel.toKtorLogLevel(): LogLevel =
    when (this) {
        SwiftNetworkLogLevel.ALL -> LogLevel.ALL
        SwiftNetworkLogLevel.HEADERS -> LogLevel.HEADERS
        SwiftNetworkLogLevel.BODY -> LogLevel.BODY
        SwiftNetworkLogLevel.INFO -> LogLevel.INFO
        SwiftNetworkLogLevel.NONE -> LogLevel.NONE
    }

/**
 * Configuration which is used to configure the Ktor logging plugin
 */
public data class SwiftNetworkLoggerConfig(
    public val logLevel: SwiftNetworkLogLevel,
    public val output: (message: String) -> Unit,
)

/**
 * Creates a Ktor [SyncClientConfiguration.ExtendedConfig] that extends the default Ktor client.
 * Specifying a [SwiftNetworkLoggerConfig] will install the Ktor logging plugin with the specified configuration.
 */
public fun createExtendedSyncClientConfiguration(loggingConfig: SwiftNetworkLoggerConfig? = null): SyncClientConfiguration.ExtendedConfig =
    SyncClientConfiguration.ExtendedConfig {
        if (loggingConfig != null) {
            install(Logging) {
                // Pass everything to the provided logger. The logger controls the active level
                level = loggingConfig.logLevel.toKtorLogLevel()
                logger =
                    object : KtorLogger {
                        override fun log(message: String) {
                            loggingConfig.output(message)
                        }
                    }
            }
        }
    }
