package co.powersync

import co.powersync.db.Database

class SDK (databaseDriverFactory: DatabaseDriverFactory) {
    private val database = Database(databaseDriverFactory)

    fun powersyncVersion(): String {
        return database.getPowersyncVersion();
    }
}