package com.powersync.integrations.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.turbine.turbineScope
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import com.powersync.PowerSyncDatabase
import com.powersync.db.getString
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PowerSyncRoomTest {
    lateinit var database: TestDatabase

    @BeforeTest
    fun setup() {
        val driver =
            BundledSQLiteDriver().also {
                it.loadPowerSyncExtension()
            }

        database = createDatabaseBuilder().setDriver(driver).build()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomWritePowerSyncRead() =
        runTest {
            database.userDao().create(User(id = "test", name = "Test user"))
            val logger = Logger(loggerConfigInit())

            val powersync =
                PowerSyncDatabase.opened(
                    pool = RoomConnectionPool(database),
                    scope = this,
                    schema = TestDatabase.schema,
                    identifier = "test",
                    logger = logger,
                )

            val row =
                powersync.get("SELECT * FROM user") {
                    User(
                        id = it.getString("id"),
                        name = it.getString("name"),
                    )
                }
            row shouldBe User(id = "test", name = "Test user")

            powersync.close()
        }

    @Test
    fun roomWritePowerSyncWatch() =
        runTest {
            val logger = Logger(loggerConfigInit())
            val pool = RoomConnectionPool(database)

            val powersync =
                PowerSyncDatabase.opened(
                    pool = pool,
                    scope = this,
                    schema = TestDatabase.schema,
                    identifier = "test",
                    logger = logger,
                )

            turbineScope {
                val turbine =
                    powersync
                        .watch("SELECT * FROM user") {
                            User(
                                id = it.getString("id"),
                                name = it.getString("name"),
                            )
                        }.testIn(this)

                turbine.awaitItem() shouldHaveSize 0
                database.userDao().create(User("id", "name"))
                pool.transferRoomUpdatesToPowerSync() // TODO: Would be cool if this wasn't necessary
                turbine.awaitItem() shouldHaveSize 1
                turbine.cancel()
            }
        }

    @Test
    fun powersyncWriteRoomRead() =
        runTest {
            val logger = Logger(loggerConfigInit())
            val pool = RoomConnectionPool(database)

            val powersync =
                PowerSyncDatabase.opened(
                    pool = pool,
                    scope = this,
                    schema = TestDatabase.schema,
                    identifier = "test",
                    logger = logger,
                )

            database.userDao().getAll() shouldHaveSize 0
            powersync.execute("insert into user values (uuid(), ?)", listOf("PowerSync user"))
            database.userDao().getAll() shouldHaveSize 1
        }

    @Test
    fun powersyncWriteRoomWatch() =
        runTest {
            val logger = Logger(loggerConfigInit())
            val pool = RoomConnectionPool(database)

            val powersync =
                PowerSyncDatabase.opened(
                    pool = pool,
                    scope = this,
                    schema = TestDatabase.schema,
                    identifier = "test",
                    logger = logger,
                )

            turbineScope {
                val turbine = database.userDao().watchAll().testIn(this)
                turbine.awaitItem() shouldHaveSize 0

                powersync.execute("insert into user values (uuid(), ?)", listOf("PowerSync user"))
                turbine.awaitItem() shouldHaveSize 1
                turbine.cancel()
            }
        }
}
