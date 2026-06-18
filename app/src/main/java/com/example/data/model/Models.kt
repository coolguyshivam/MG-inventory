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
    val underRepair: Boolean = false,
    val technicianName: String? = null,
    val repairReason: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isUnderRepair: Boolean
        @com.google.firebase.firestore.Exclude
        @androidx.room.Ignore
        get() = underRepair
}

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

data class BrandStockItem(
    val id: String = UUID.randomUUID().toString(),
    val imei: String = "",
    val brand: String = "",
    val variant: String = "",
    val color: String = "",
    val warehouse: String = "G", // "G" or "O"
    val addedByUser: String = "",
    val addedDate: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class BrandStockTransaction(
    val id: String = UUID.randomUUID().toString(),
    val imei: String = "",
    val brand: String = "",
    val variant: String = "",
    val color: String = "",
    val warehouse: String = "G", // "G" or "O"
    val type: String = "IN", // "IN" or "OUT"
    val dateInMillis: Long = System.currentTimeMillis(),
    val operator: String = "",
    val notes: String? = null
)

data class BrandVariant(
    val id: String = UUID.randomUUID().toString(),
    val brand: String = "",
    val modelName: String = "", // e.g., "Oppo Reno 11 Pro"
    val specs: String = "",     // e.g., "12GB/256GB"
    val color: String = ""      // e.g., "Wave Green"
)



