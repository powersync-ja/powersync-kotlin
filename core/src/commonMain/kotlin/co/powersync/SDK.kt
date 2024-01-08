package co.powersync

class SDK (databaseDriverFactory: DatabaseDriverFactory) {
    private val database = Database(databaseDriverFactory)

    fun powersyncVersion(): String {
        return database.getPowersyncVersion();
    }
}