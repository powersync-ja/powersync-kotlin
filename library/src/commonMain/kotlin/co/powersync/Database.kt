package co.powersync

class Database(databaseDriverFactory: DatabaseDriverFactory) {
    private val database = AppDatabase(databaseDriverFactory.createDriver())
    private val dbQuery = database.playerQueries

    internal fun getAllPlayers(): List<HockeyPlayer> {
        return dbQuery.selectAll().executeAsList()
    }
}