package com.powersync

import com.powersync.db.NativeConnectionFactory

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
public actual class DatabaseDriverFactory : NativeConnectionFactory() {
    actual override fun resolveDefaultDatabasePath(dbFilename: String): String = appleDefaultDatabasePath(dbFilename)
}

internal actual val inMemoryDriver: InMemoryConnectionFactory = DatabaseDriverFactory()
