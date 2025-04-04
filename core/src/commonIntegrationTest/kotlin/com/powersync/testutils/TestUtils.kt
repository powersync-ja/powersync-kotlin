package com.powersync.testutils

import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestConfig
import co.touchlab.kermit.TestLogWriter
import com.powersync.DatabaseDriverFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.coroutines.CoroutineContext

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

@OptIn(ExperimentalKermitApi::class)
class DatabaseTestScope : CoroutineContext.Element {
    val logWriter =
        TestLogWriter(
            loggable = Severity.Debug,
        )
    val logger =
        Logger(
            TestConfig(
                minSeverity = Severity.Debug,
                logWriterList = listOf(logWriter, generatePrintLogWriter()),
            ),
        )

    val testDirectory by lazy {
        getTempDir() ?: SystemFileSystem.resolve(Path(".")).name
    }

    val databaseName by lazy {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        CharArray(8) { allowedChars.random() }.concatToString()
    }

    private val cleanupItems: MutableList<suspend () -> Unit> = mutableListOf()

    override val key: CoroutineContext.Key<*>
        get() = Companion

    companion object : CoroutineContext.Key<DatabaseTestScope>
}

val CoroutineContext.database: DatabaseTestScope get() = get(DatabaseTestScope) ?: error("Not in PowerSync test: $this")

fun databaseTest(
    testBody: suspend TestScope.() -> Unit
) = runTest {
    val test = DatabaseTestScope()

    withContext(test) {
        testBody()
    }
}
