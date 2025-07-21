package com.powersync

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter.LogEntry
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

/**
 * A version of the `TestLogWriter` from Kermit that uses a mutex around logs instead of throwing
 * for concurrent access.
*/
@OptIn(ExperimentalKermitApi::class)
class PowerSyncTestLogWriter(
    private val loggable: Severity,
) : LogWriter() {
    private val lock = reentrantLock()
    private val _logs = mutableListOf<LogEntry>()

    val logs: List<LogEntry>
        get() = lock.withLock { _logs.toList() }

    override fun isLoggable(
        tag: String,
        severity: Severity,
    ): Boolean = severity.ordinal >= loggable.ordinal

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        lock.withLock {
            _logs.add(LogEntry(severity, message, tag, throwable))
        }
    }
}
