package com.powersync.test

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.reentrantLock
import io.ktor.utils.io.locks.withLock

/**
 * A version of the `TestLogWriter` from Kermit that uses a mutex around logs instead of throwing
 * for concurrent access.
*/
@OptIn(ExperimentalKermitApi::class, InternalAPI::class)
class PowerSyncTestLogWriter(
    private val loggable: Severity,
) : LogWriter() {
    private val lock = reentrantLock()
    private val _logs = mutableListOf<TestLogWriter.LogEntry>()

    val logs: List<TestLogWriter.LogEntry>
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
            _logs.add(TestLogWriter.LogEntry(severity, message, tag, throwable))
        }
    }
}
