package com.powersync

import androidx.sqlite.SQLiteConnection
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class DatabaseDriverFactory: PersistentDriverFactory {
    override val platform: PowerSyncPlatform

    override fun resolveDefaultDatabasePath(dbFilename: String): String
    override fun openConnection(path: String, openFlags: Int): SQLiteConnection
}

internal expect fun openInMemoryConnection(): SQLiteConnection

internal object BuiltinPlatform: PowerSyncPlatform {
    override fun openInMemoryConnection(): SQLiteConnection {
        return com.powersync.openInMemoryConnection()
    }

    override fun configureHttpClient(block: HttpClientConfig<*>.() -> Unit) = HttpClient(block)
}
