package com.powersync

import androidx.sqlite.SQLiteConnection
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

public interface PowerSyncPlatform {
    public fun openInMemoryConnection(): SQLiteConnection

    public fun configureHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
}

public interface PersistentDriverFactory {
    public val platform: PowerSyncPlatform

    public fun resolveDefaultDatabasePath(dbFilename: String): String

    /**
     * Opens a SQLite connection on [path] with [openFlags].
     *
     * The connection should have the PowerSync core extension loaded.
     */
    public fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection

    public fun openConnection(
        dbFilename: String,
        dbDirectory: String?,
        readOnly: Boolean = false,
    ): SQLiteConnection {
        val dbPath =
            if (dbDirectory != null) {
                "$dbDirectory/$dbFilename"
            } else {
                resolveDefaultDatabasePath(dbFilename)
            }

        return openConnection(
            dbPath,
            if (readOnly) {
                SQLITE_OPEN_READONLY
            } else {
                SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE
            },
        )
    }
}

/**
 * Resolves a path to the loadable PowerSync core extension library.
 *
 * This library must be loaded on all databases using the PowerSync SDK. On platforms where the
 * extension is linked statically (only watchOS at the moment), this returns `null`.
 *
 * When using the PowerSync SDK directly, there is no need to invoke this method. It is intended for
 * configuring external database connections not managed by PowerSync to work with the PowerSync
 * SDK.
 */
@ExperimentalPowerSyncAPI
@Throws(PowerSyncException::class)
public expect fun resolvePowerSyncLoadableExtensionPath(): String?

private const val SQLITE_OPEN_READONLY = 0x01
private const val SQLITE_OPEN_READWRITE = 0x02
private const val SQLITE_OPEN_CREATE = 0x04

