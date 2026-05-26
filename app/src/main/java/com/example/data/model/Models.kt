package com.example.data.model

import java.util.UUID

// @Entity(tableName = "inventory_items")
data class InventoryItem(
    val id: String = UUID.randomUUID().toString(),
    val serialNumber: String = "",
    val model: String = "",
    val name: String = "",
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

// @Entity(tableName = "history_events")
data class HistoryEvent(
    val id: String = UUID.randomUUID().toString(),
    val actionType: String = "",
    val serialNumber: String = "",
    val model: String = "",
    val name: String = "",
    val phoneNumber: String? = null,
    val aadhaarNumber: String? = null,
    val amount: Double = 0.0,
    val description: String = "",
    val dateInMillis: Long = System.currentTimeMillis(),
    val quantity: Int = 1,
    val photoUri: String? = null,
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val extraDetails: String? = null
)

