package com.powersync

import androidx.sqlite.SQLiteConnection

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public expect class DatabaseDriverFactory : PersistentConnectionFactory {
    override fun openInMemoryConnection(): SQLiteConnection

    override fun resolveDefaultDatabasePath(dbFilename: String): String

    override fun openConnection(
        path: String,
        openFlags: Int,
    ): SQLiteConnection
}

internal expect val inMemoryDriver: InMemoryConnectionFactory
