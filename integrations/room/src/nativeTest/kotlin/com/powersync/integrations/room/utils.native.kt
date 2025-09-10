package com.powersync.integrations.room

import androidx.room.Room
import androidx.room.RoomDatabase

actual fun createDatabaseBuilder(): RoomDatabase.Builder<TestDatabase> {
    return Room.inMemoryDatabaseBuilder()
}
