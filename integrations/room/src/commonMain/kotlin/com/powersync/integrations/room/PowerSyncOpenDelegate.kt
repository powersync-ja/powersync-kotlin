package com.powersync.integrations.room

import androidx.room.RoomOpenDelegate
import androidx.sqlite.SQLiteConnection

public class PowerSyncOpenDelegate(): RoomOpenDelegate(1, "", "") {
    override fun onCreate(connection: SQLiteConnection) {
    }

    override fun onPreMigrate(connection: SQLiteConnection) {
    }

    override fun onValidateSchema(connection: SQLiteConnection): ValidationResult {
        return ValidationResult(true, null)
    }

    override fun onPostMigrate(connection: SQLiteConnection) {
    }

    override fun onOpen(connection: SQLiteConnection) {

    }

    override fun createAllTables(connection: SQLiteConnection) {

    }

    override fun dropAllTables(connection: SQLiteConnection) {
    }

}