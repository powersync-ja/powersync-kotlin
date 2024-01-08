package co.powersync

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor

class Database(databaseDriverFactory: DatabaseDriverFactory) {
    private val driver = databaseDriverFactory.createDriver();
    private val database = AppDatabase(driver)
    private val dbQuery = database.playerQueries

    internal fun getAllPlayers(): List<HockeyPlayer> {
        return dbQuery.selectAll().executeAsList()
    }


    fun getPowersyncVersion():  String {
        return PowerSyncVersion() { cursor ->
            cursor.getString(0)!!
        }.executeAsOne()
    }


    private inner class PowerSyncVersion<out T : Any>(
        mapper: (SqlCursor) -> T,
    ) : Query<T>(mapper) {
        override fun addListener(listener: Query.Listener) {
            driver.addListener("hockeyPlayer", listener = listener)
        }

        override fun removeListener(listener: Query.Listener) {
            driver.removeListener("hockeyPlayer", listener = listener)
        }

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
            driver.executeQuery(null, """
                |SELECT powersync_rs_version()
                """.trimMargin(), mapper, 0)

        override fun toString(): String = "powerSyncVersion"
    }
}