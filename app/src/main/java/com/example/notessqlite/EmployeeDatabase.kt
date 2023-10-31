package com.example.notessqlite

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import co.powersync.kotlin.DatabaseHelper
import co.powersync.kotlin.PowerSyncDatabase
import co.powersync.kotlin.SupaBaseConnector
import co.powersync.kotlin.db.Column
import co.powersync.kotlin.db.ColumnType
import co.powersync.kotlin.db.Index
import co.powersync.kotlin.db.IndexedColumn
import co.powersync.kotlin.db.Schema
import co.powersync.kotlin.db.Table
import kotlinx.coroutines.runBlocking

class EmployeeDatabase(context: Context) {

    companion object {
        private val SCHEMA: Schema = Schema( tables = arrayOf(
            Table(
                name = "todos",
                columns = arrayOf(
                    Column(name =  "list_id", type = ColumnType.TEXT ),
                    Column(name =  "created_at", type = ColumnType.TEXT ),
                    Column(name =  "completed_at", type = ColumnType.TEXT ),
                    Column(name =  "description", type = ColumnType.TEXT ),
                    Column(name =  "completed", type = ColumnType.INTEGER ),
                    Column(name =  "created_by", type = ColumnType.TEXT ),
                    Column(name =  "completed_by", type = ColumnType.TEXT)
                ),
                indexes = arrayOf(
                    Index(
                        name = "list",
                        columns= arrayOf(
                            IndexedColumn(name = "list_id")
                        ))
                )
            ),
            Table(
                name= "lists",
                columns = arrayOf(
                    Column(name = "created_at", type =  ColumnType.TEXT),
                    Column(name = "name", type =  ColumnType.TEXT),
                    Column(name = "owner_id", type =  ColumnType.TEXT)
                )
            )
        )
        )
    }

    private val databaseHelper = DatabaseHelper(context)
    private val supaBaseClient = SupaBaseConnector();
    private val powerSyncDatabase = PowerSyncDatabase(database = databaseHelper.writableDatabase, schema = SCHEMA, connector = supaBaseClient);

    fun init(){
        runBlocking {
            powerSyncDatabase.connect();
        }
    }

    fun addEmployee(employee: Employee){
        insert(employee.name, employee.code, employee.image);
    }

    fun getAllEmployees(): List<Employee>{
        return getAll()
    }

    fun insert(name: String, code: String, image: ByteArray) {
        // get the writable database
        val db = databaseHelper.writableDatabase

        // create the ContentValues object
        val values = ContentValues().apply{
            put("employee_name", name)
            put("employee_code", code)
            put("employee_image", image)
        }

        // insert the data into the table
        db.insert("employee", null, values)

        // close the database connection
        db.close()
    }

    @SuppressLint("Range")
    fun getAll(): List<Employee> {
        val list = mutableListOf<Employee>()

        // get the readable database
        val db = databaseHelper.readableDatabase

        // select all data from the table
        val cursor = db.rawQuery("SELECT * FROM employee", null)

        // iterate through the cursor and add the data to the list
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndex("employee_name"))
            val code = cursor.getString(cursor.getColumnIndex("employee_code"))
            val image = cursor.getBlob(cursor.getColumnIndex("employee_image"))
            list.add(Employee(name, code, image))
        }

        // close the cursor and database connection
        cursor.close()
        db.close()

        return list
    }

    fun update(id: Int, name: String, code: String, image: ByteArray) {
        // get the writable database
        val db = databaseHelper.writableDatabase

        // create the ContentValues object
        val values = ContentValues().apply {
            put("employee_name", name)
            put("employee_code", code)
            put("employee_image", image)
        }

        // update the data in the table
        db.update("employee", values, "id = ?", arrayOf(id.toString()))

        // close the database connection
        db.close()
    }

    fun delete(id: Int) {
        // get the writable database
        val db = databaseHelper.writableDatabase

        // delete the data from the table
        db.delete("employee", "id = ?", arrayOf(id.toString()))

        // close the database connection
        db.close()
    }

    data class Employee(val name: String, val code: String, val image: ByteArray)

}