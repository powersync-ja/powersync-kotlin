package com.powersync.integrations.room

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun createDatabaseBuilder(): RoomDatabase.Builder<TestDatabase> {
    return Room.inMemoryDatabaseBuilder<TestDatabase>()
}
