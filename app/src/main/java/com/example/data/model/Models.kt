package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
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
    val repairReason: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "history_events")
data class HistoryEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
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

data class Party(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phoneNumber: String = "",
    val aadhaarNumber: String = "",
    val address: String = "",
    val balance: Double = 0.0 // positive = they owe us, negative = we owe them
)

data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val partyId: String = "",
    val amount: Double = 0.0,
    val type: String = "", // "PAYMENT_IN", "PAYMENT_OUT", "PURCHASE", "SALE", "RETURN", "REPAIR"
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val historyEventId: String? = null
)

data class AttendanceRecord(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val userName: String = "",
    val dateString: String = "", // "yyyy-MM-dd"
    val checkInTime: Long = 0,
    val checkInSelfieBase64: String? = null,
    val checkInLocationSpec: String? = null,
    val checkOutTime: Long = 0,
    val checkOutSelfieBase64: String? = null,
    val checkOutLocationSpec: String? = null,
    val status: String = "Present", // "Present", "On Leave"
    val notes: String? = null
)

data class LeaveApplication(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val userName: String = "",
    val startDateString: String = "", // "yyyy-MM-dd"
    val endDateString: String = "",   // "yyyy-MM-dd"
    val leaveType: String = "Sick Leave", // "Sick Leave", "Casual Leave", "Earned Leave"
    val reason: String = "",
    val status: String = "Pending",     // "Pending", "Approved", "Rejected"
    val approvedBy: String? = null,
    val appliedOn: Long = System.currentTimeMillis()
)

data class NotificationLog(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "INFO" // "INFO", "CHECK_IN", "CHECK_OUT"
)



