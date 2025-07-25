package com.powersync.integrations.room

import androidx.room.RoomDatabase

actual val factory: com.powersync.DatabaseDriverFactory
    get() = throw UnsupportedOperationException("Android unit tests are not supported")

actual fun databaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    throw UnsupportedOperationException("Android unit tests are not supported")
}
