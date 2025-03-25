package com.powersync.testutils

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.powersync.DatabaseDriverFactory

expect val factory: DatabaseDriverFactory

expect fun cleanup(path: String)

expect fun getTempDir(): String?

fun generatePrintLogWriter() =
    object : LogWriter() {
        override fun log(
            severity: Severity,
            message: String,
            tag: String,
            throwable: Throwable?,
        ) {
            println("[$severity:$tag] - $message")
        }
    }
