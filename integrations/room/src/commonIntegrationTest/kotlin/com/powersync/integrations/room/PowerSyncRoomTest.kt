package com.powersync.integrations.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.turbine.turbineScope
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
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
        logger.i { "Closing Room database" }
        database.close()
    }

    @Test
    fun roomWritePowerSyncRead() =
        runTest {
            database.userDao().create(User(id = "test", name = "Test user"))

            val powersync =
                PowerSyncDatabase.opened(
                    pool = RoomConnectionPool(database, TestDatabase.schema),
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
            val pool = RoomConnectionPool(database, TestDatabase.schema)

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
                turbine.awaitItem() shouldHaveSize 1
                turbine.cancel()
            }

            powersync.close()
        }

    @Test
    fun powersyncWriteRoomRead() =
        runTest {
            val pool = RoomConnectionPool(database, TestDatabase.schema)

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
            powersync.close()
        }

    @Test
    fun powersyncWriteRoomWatch() =
        runTest {
            val pool = RoomConnectionPool(database, TestDatabase.schema)

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

            powersync.close()
        }

    @Test
    fun canWriteToVirtualTables() =
        runTest {
            val pool = RoomConnectionPool(database, TestDatabase.schema)
            val powersync =
                PowerSyncDatabase.opened(
                    pool = pool,
                    scope = this,
                    schema = TestDatabase.schema,
                    identifier = "test",
                    logger = logger,
                )

            // After inserting into a virtual table, we should not report shadow tables as updates to
            // Room as that causes a crash. This also means that virtual table updates can't be watched
            // from Room, but that's a Room limitation.
            powersync.execute("INSERT into users_fts(id, name) VAlUES (uuid(), ?)", listOf("test name"))
        }

    companion object {
        private val logger = Logger(loggerConfigInit(CommonWriter()))
    }
}
