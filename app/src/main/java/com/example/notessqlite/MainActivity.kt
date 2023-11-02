package com.example.notessqlite

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {

    private lateinit var employeeRecyclerView: RecyclerView
    private lateinit var employeeAdapter: EmployeeAdapter

    private lateinit var database: EmployeeDatabase

//    private lateinit var nativeSqliteSDK: NativeSqliteSDK;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        nativeSqliteSDK = NativeSqliteSDK();
//        nativeSqliteSDK.initialize();

        employeeRecyclerView = findViewById(R.id.rv_employee_list)

        employeeAdapter = EmployeeAdapter(emptyList())
        employeeRecyclerView.adapter = employeeAdapter

        database = EmployeeDatabase(this)
        database.init()

        // Add sample data to the database
        //database.addEmployee(EmployeeDatabase.Employee("John Smith", "JNS001", getByteArrayFromDrawable(R.drawable.employee1)))
        //database.addEmployee(EmployeeDatabase.Employee("Jane Doe", "JDO002", getByteArrayFromDrawable(R.drawable.employee2)))
        //database.addEmployee(EmployeeDatabase.Employee("Bob Johnson", "BJH003", getByteArrayFromDrawable(R.drawable.employee3)))

        // Get all employees from the database and display them in the RecyclerView
//        val employeeList = database.getAllEmployees()
//        employeeAdapter = EmployeeAdapter(employeeList)
        employeeRecyclerView.adapter = employeeAdapter
        employeeRecyclerView.layoutManager = LinearLayoutManager(this)

        println("Done with setup")
    }

    // Helper function to convert a drawable resource to a byte array
    private fun getByteArrayFromDrawable(drawableId: Int): ByteArray {
        val drawable = ContextCompat.getDrawable(this, drawableId) ?: throw IllegalArgumentException("Drawable not found")
        val bitmap = (drawable as BitmapDrawable).bitmap
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}