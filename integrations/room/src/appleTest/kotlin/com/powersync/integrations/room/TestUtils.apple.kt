package com.powersync.integrations.room

import androidx.room.Room
import androidx.room.RoomDatabase
import com.powersync.DatabaseDriverFactory

actual val factory: DatabaseDriverFactory
    get() = DatabaseDriverFactory()

actual fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder("TestDatabase") {
        AppDatabaseForPowerSync(
            createTodosDao = { TodosDao_Impl(it) }
        )
    }
}
