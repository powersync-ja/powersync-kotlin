package com.powersync.utils

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/*
 * Generates a logger with the appropriate severity level based on the build type
 * if no Logger is provided.
*/
public fun generateLogger(logger: Logger?): Logger {
    if(logger != null) {
        return logger
    }

    val defaultLogger: Logger = Logger

    if(BuildConfig.isDebug) {
        Logger.setMinSeverity(Severity.Verbose)
    } else {
        Logger.setMinSeverity(Severity.Warn)
    }

    return defaultLogger
}