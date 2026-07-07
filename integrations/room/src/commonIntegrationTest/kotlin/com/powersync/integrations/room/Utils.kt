package com.powersync.integrations.room

import androidx.room3.RoomDatabase

expect fun createDatabaseBuilder(): RoomDatabase.Builder<TestDatabase>
