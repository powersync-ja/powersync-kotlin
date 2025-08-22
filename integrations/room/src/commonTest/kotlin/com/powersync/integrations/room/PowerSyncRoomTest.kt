package com.powersync.integrations.room

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import co.touchlab.kermit.Logger
import co.touchlab.kermit.LoggerConfig
import co.touchlab.kermit.loggerConfigInit
import com.powersync.PowerSyncDatabase
import com.powersync.addPowerSyncExtension
import com.powersync.db.getString
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PowerSyncRoomTest {

    lateinit var database: TestDatabase

    @BeforeTest
    fun setup() {
        val driver = BundledSQLiteDriver().also {
            it.addPowerSyncExtension()
        }

        database = createDatabaseBuilder().setDriver(driver).build()
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomWritePowerSyncRead() = runTest {
        database.userDao().create(User(id = "test", name = "Test user"))
        val logger = Logger(loggerConfigInit())

        val powersync = PowerSyncDatabase.opened(
            pool = RoomConnectionPool(database, this),
            scope = this,
            schema = TestDatabase.schema,
            group = PowerSyncDatabase.databaseGroup(logger, "test"),
            logger = logger,
        )

        val row = powersync.get("SELECT * FROM user") {
            User(
                id = it.getString("id"),
                name = it.getString("name")
            )
        }
        row shouldBe User(id = "test", name = "Test user")

        powersync.close()
    }
}
