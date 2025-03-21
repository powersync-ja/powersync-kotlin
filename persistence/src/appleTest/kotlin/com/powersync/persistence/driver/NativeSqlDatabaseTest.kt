package com.powersync.persistence.driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.native.NativeDatabaseManager
import kotlin.test.BeforeTest
import kotlin.test.Test

class NativeSqlDatabaseTest {

    lateinit var manager: NativeDatabaseManager

    @BeforeTest
    fun setup() {
        manager = createDatabaseManager(DatabaseConfiguration(
            name = null,
            version = 1,
            create = {},
            inMemory = true,
        )) as NativeDatabaseManager
    }

    @Test
    fun canOpenDatabases() {
        val driver = NativeSqliteDriver(manager, 1)
        fun map(cursor: SqlCursor): QueryResult<String?> {
            return QueryResult.Value(cursor.getString(0))
        }

        driver.executeQuery(null, "SELECT sqlite_version() AS r;", ::map, 0, null)
    }
}
