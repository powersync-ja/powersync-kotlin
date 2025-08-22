package com.powersync.integrations.room

import androidx.room.RoomDatabase

expect fun createDatabaseBuilder(): RoomDatabase.Builder<TestDatabase>
