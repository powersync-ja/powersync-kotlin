package co.powersync.kotlin

import android.content.Context
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import io.requery.android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION){
    companion object {
        private const val DATABASE_NAME = "employee.db"
        private const val DATABASE_VERSION = 1

        private const val CREATE_TABLE_EMPLOYEE = """
            CREATE TABLE employee (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                employee_name TEXT,
                employee_code TEXT,
                employee_image BLOB
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        // create the employee table
        db.execSQL(CREATE_TABLE_EMPLOYEE)
    }

    override fun onOpen(db: SQLiteDatabase?) {
        super.onOpen(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // drop the employee table if it exists
        db.execSQL("DROP TABLE IF EXISTS employee")

        // create the new employee table
        onCreate(db)
    }

    // This provides the config each time a connection is made.
    override fun createConfiguration(path: String?, openFlags: Int): SQLiteDatabaseConfiguration {
        val config = super.createConfiguration(path, openFlags);

        config.customExtensions += SQLiteCustomExtension("libpowersync", "sqlite3_powersync_init");

        return config;
    }
}