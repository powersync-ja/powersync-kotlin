package com.powersync

import com.powersync.db.NativeConnectionFactory
import com.powersync.internal.InternalPowerSyncAPI
import com.powersync.internal.httpClientIsKnownToNotSupportBackpressure
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class, InternalPowerSyncAPI::class)
@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
public actual class DatabaseDriverFactory : NativeConnectionFactory() {
    init {
        // Hack: Install apple-specific httpClientIsKnownToNotSupportBackpressure hook.
        httpClientIsKnownToNotSupportBackpressure.compareAndSet(null, ::appleClientKnownNotSupportBackpressure)
    }

    actual override fun resolveDefaultDatabasePath(dbFilename: String): String = appleDefaultDatabasePath(dbFilename)
}

private fun appleClientKnownNotSupportBackpressure(config: HttpClientEngineConfig): Boolean = config is DarwinClientEngineConfig

internal actual val inMemoryDriver: InMemoryConnectionFactory = DatabaseDriverFactory()
