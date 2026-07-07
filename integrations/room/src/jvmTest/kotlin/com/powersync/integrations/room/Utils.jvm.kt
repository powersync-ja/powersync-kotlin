package com.powersync.integrations.room

import androidx.room3.Room
import androidx.room3.RoomDatabase

actual fun createDatabaseBuilder(): RoomDatabase.Builder<TestDatabase> =
    Room.inMemoryDatabaseBuilder<TestDatabase>().addCallback(TestDatabase)
