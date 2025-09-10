package com.powersync.integrations.room

import androidx.room.RoomDatabase

actual fun createDatabaseBuilder(): RoomDatabase.Builder<TestDatabase> {
    TODO("Android unit tests are unsupported, we test on JVM instead")
}
