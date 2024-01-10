package co.powersync.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import co.powersync.db.PsDatabase

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
        override fun addListener(listener: Listener) {
            driver.addListener("hockeyPlayer", listener = listener)
        }

        override fun removeListener(listener: Listener) {
            driver.removeListener("hockeyPlayer", listener = listener)
        }

        override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
            driver.executeQuery(null, """
                |SELECT powersync_rs_version()
                """.trimMargin(), mapper, 0)

        override fun toString(): String = "powerSyncVersion"
    }
}