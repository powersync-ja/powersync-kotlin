package com.example.notessqlite

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmployeeAdapter(private val employeeList: List<EmployeeDatabase.Employee>) : RecyclerView.Adapter<EmployeeAdapter.EmployeeViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmployeeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_employee, parent, false)
        return EmployeeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmployeeViewHolder, position: Int) {
        val employee = employeeList[position]
        holder.bind(employee)
    }

    override fun getItemCount(): Int = employeeList.size

    class EmployeeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(employee: EmployeeDatabase.Employee) {
            itemView.findViewById<TextView>(R.id.tv_employee_name).text = employee.name
            itemView.findViewById<TextView>(R.id.tv_employee_code).text = employee.code

            itemView.findViewById<ImageView>(R.id.iv_employee_image)
                .setImageBitmap(BitmapFactory.decodeByteArray(employee.image, 0, employee.image.size))
        }
    }
}