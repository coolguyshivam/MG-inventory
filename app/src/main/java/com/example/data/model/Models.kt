package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "history_events")
data class HistoryEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val actionType: String, // PURCHASE, SALE, RETURN, REPAIR_SENT, REPAIR_RETURNED
    val model: String,
    val name: String,
    val phoneNumber: String?,
    val serialNumber: String,
    val amount: Double,
    val aadhaarNumber: String?,
    val quantity: Int = 1,
    val description: String?, // Stores description and Address ("Address: <address>\n<description>")
    val photoUri: String?, // Store comma-separated local / remote URLs
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val model: String,
    val serialNumber: String, // IMEI / Serial number
    val price: Double,
    val quantity: Int = 1,
    val supplierOrCustomerName: String,
    val status: String, // STOCK, SOLD, REPAIR, RETURNED
    val updatedTimestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phoneNumber: String,
    val role: String, // Manager, Sales Staff, Technician, Cashier
    val status: String = "Active", // Active, Inactive
    val dateJoined: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: Int,
    val employeeName: String,
    val date: String, // yyyy-MM-dd
    val status: String, // Present, Absent, Late, Half Day
    val checkInTime: String?, // e.g. "09:30 AM"
    val checkOutTime: String?, // e.g. "06:30 PM"
    val remarks: String?,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
