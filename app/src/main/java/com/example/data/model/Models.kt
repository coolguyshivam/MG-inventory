package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serialNumber: String,
    val model: String,
    val name: String,
    val phoneNumber: String? = null,
    val aadhaarNumber: String? = null,
    val amount: Double = 0.0,
    val description: String = "",
    val dateInMillis: Long = System.currentTimeMillis(),
    val quantity: Int = 1,
    val photoUri: String? = null,
    val isUnderRepair: Boolean = false,
    val technicianName: String? = null,
    val repairReason: String? = null
)

@Entity(tableName = "history_events")
data class HistoryEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val actionType: String, // "PURCHASE", "SALE", "REPAIR_SENT", "REPAIR_RETURNED", "RETURN", "EDIT", "DELETE"
    val serialNumber: String,
    val model: String,
    val name: String,
    val phoneNumber: String? = null,
    val aadhaarNumber: String? = null,
    val amount: Double = 0.0,
    val description: String = "",
    val dateInMillis: Long = System.currentTimeMillis(),
    val quantity: Int = 1,
    val photoUri: String? = null,
    val userId: String, // audit tracking e.g. "admin"
    val timestamp: Long = System.currentTimeMillis(),
    val extraDetails: String? = null // e.g., Technician, Reason, etc.
)
