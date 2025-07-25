package com.powersync.integrations.room

import androidx.room.RoomDatabase
import com.powersync.DatabaseDriverFactory

expect val factory: DatabaseDriverFactory

expect fun databaseBuilder(): RoomDatabase.Builder<AppDatabase>
