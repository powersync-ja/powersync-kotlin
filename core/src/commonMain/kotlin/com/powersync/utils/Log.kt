package com.powersync.utils

import BuildConfig
import co.touchlab.kermit.*

/*
 * Generates a logger with the appropriate severity level based on the build type
 * if no Logger is provided.
*/
public fun generateLogger(logger: Logger?): Logger {
    return logger
        ?: Logger(
            config = StaticConfig(
                logWriterList = listOf(platformLogWriter()),
                minSeverity = if (BuildConfig.isDebug)
                    Severity.Verbose
                else
                    Severity.Warn
            ),
            tag = "PowerSync",
        )
}
