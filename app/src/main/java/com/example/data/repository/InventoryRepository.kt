package com.example.data.repository

import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import com.example.data.model.User
import com.example.data.model.AttendanceRecord
import com.example.data.model.LeaveApplication
import com.example.data.model.NotificationLog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class InventoryRepository {
    private val db = FirebaseFirestore.getInstance()

    val allInventoryItems: Flow<List<InventoryItem>> = callbackFlow {
        val sub = db.collection("inventory_items").addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(InventoryItem::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allHistoryEvents: Flow<List<HistoryEvent>> = callbackFlow {
        val sub = db.collection("history_events").addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(HistoryEvent::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allUsers: Flow<List<User>> = callbackFlow {
        val sub = db.collection("users").addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(User::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allParties: Flow<List<com.example.data.model.Party>> = callbackFlow {
        val sub = db.collection("parties").addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(com.example.data.model.Party::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allLedgerEntries: Flow<List<com.example.data.model.LedgerEntry>> = callbackFlow {
        val sub = db.collection("ledger_entries").addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(com.example.data.model.LedgerEntry::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allAttendanceRecords: Flow<List<AttendanceRecord>> = callbackFlow {
        val sub = db.collection("attendance_records").addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(AttendanceRecord::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allLeaveApplications: Flow<List<LeaveApplication>> = callbackFlow {
        val sub = db.collection("leave_applications").addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(LeaveApplication::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allNotifications: Flow<List<NotificationLog>> = callbackFlow {
        val sub = db.collection("attendance_notifications").addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(NotificationLog::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    suspend fun getUserCount(): Int {
        return try {
            db.collection("users").get().await().size()
        } catch(e:Exception) { 0 }
    }

    suspend fun getUserByUsername(username: String): User? {
        return try {
            db.collection("users").document(username).get().await().toObject(User::class.java)
        } catch(e:Exception) { null }
    }

    suspend fun insertUser(user: User) {
        db.collection("users").document(user.username).set(user).await()
    }

    suspend fun deleteUser(user: User) {
        db.collection("users").document(user.username).delete().await()
    }

    suspend fun updateUser(user: User) {
        db.collection("users").document(user.username).set(user).await()
    }

    suspend fun getItemBySerialNumber(serialNumber: String): InventoryItem? {
        val snap = db.collection("inventory_items").whereEqualTo("serialNumber", serialNumber).limit(1).get().await()
        return snap.documents.firstOrNull()?.toObject(InventoryItem::class.java)
    }

    suspend fun getItemById(id: String): InventoryItem? {
        return db.collection("inventory_items").document(id).get().await().toObject(InventoryItem::class.java)
    }

    suspend fun purchaseProduct(
        serialNumber: String,
        model: String,
        name: String,
        phoneNumber: String?,
        aadhaarNumber: String?,
        amount: Double,
        description: String,
        dateInMillis: Long,
        quantity: Int,
        photoUri: String?,
        userId: String
    ): Boolean {
        val item = InventoryItem(
            id = UUID.randomUUID().toString(),
            serialNumber = serialNumber,
            model = model,
            name = name,
            phoneNumber = phoneNumber,
            aadhaarNumber = aadhaarNumber,
            amount = amount,
            description = description,
            dateInMillis = dateInMillis,
            quantity = quantity,
            photoUri = photoUri,
            isUnderRepair = false
        )
        db.collection("inventory_items").document(item.id).set(item).await()

        val history = HistoryEvent(
            id = UUID.randomUUID().toString(),
            actionType = "PURCHASE",
            serialNumber = serialNumber,
            model = model,
            name = name,
            phoneNumber = phoneNumber,
            aadhaarNumber = aadhaarNumber,
            amount = amount,
            description = description,
            dateInMillis = dateInMillis,
            quantity = quantity,
            photoUri = photoUri,
            userId = userId
        )
        db.collection("history_events").document(history.id).set(history).await()
        processPartyTransactionIfApplicable(name, "PURCHASE", amount * quantity, history.id)
        return true
    }

    suspend fun saleProduct(
        serialNumber: String,
        model: String,
        name: String,
        phoneNumber: String?,
        aadhaarNumber: String?,
        amount: Double,
        description: String,
        dateInMillis: Long,
        quantity: Int,
        photoUri: String?,
        userId: String
    ): Boolean {
        val existing = getItemBySerialNumber(serialNumber)
        if (existing != null) {
            if (existing.quantity <= quantity) {
                db.collection("inventory_items").document(existing.id).delete().await()
            } else {
                db.collection("inventory_items").document(existing.id).update("quantity", existing.quantity - quantity).await()
            }
        }

        val history = HistoryEvent(
            id = UUID.randomUUID().toString(),
            actionType = "SALE",
            serialNumber = serialNumber,
            model = model,
            name = name,
            phoneNumber = phoneNumber,
            aadhaarNumber = aadhaarNumber,
            amount = amount,
            description = description,
            dateInMillis = dateInMillis,
            quantity = quantity,
            photoUri = photoUri ?: existing?.photoUri,
            userId = userId
        )
        db.collection("history_events").document(history.id).set(history).await()
        processPartyTransactionIfApplicable(name, "SALE", amount * quantity, history.id)
        return true
    }

    suspend fun returnProduct(
        serialNumber: String,
        model: String,
        name: String,
        phoneNumber: String?,
        aadhaarNumber: String?,
        amount: Double,
        description: String,
        dateInMillis: Long,
        quantity: Int,
        photoUri: String?,
        userId: String
    ): Boolean {
        val existing = getItemBySerialNumber(serialNumber)
        if (existing != null) {
            db.collection("inventory_items").document(existing.id).update("quantity", existing.quantity + quantity).await()
        } else {
            val item = InventoryItem(
                id = UUID.randomUUID().toString(),
                serialNumber = serialNumber,
                model = model,
                name = name,
                phoneNumber = phoneNumber,
                aadhaarNumber = aadhaarNumber,
                amount = amount,
                description = description,
                dateInMillis = dateInMillis,
                quantity = quantity,
                photoUri = photoUri,
                isUnderRepair = false
            )
            db.collection("inventory_items").document(item.id).set(item).await()
        }

        val history = HistoryEvent(
            id = UUID.randomUUID().toString(),
            actionType = "RETURN",
            serialNumber = serialNumber,
            model = model,
            name = name,
            phoneNumber = phoneNumber,
            aadhaarNumber = aadhaarNumber,
            amount = amount,
            description = description,
            dateInMillis = dateInMillis,
            quantity = quantity,
            photoUri = photoUri,
            userId = userId
        )
        db.collection("history_events").document(history.id).set(history).await()
        processPartyTransactionIfApplicable(name, "RETURN", amount * quantity, history.id)
        return true
    }

    suspend fun directRepair(
        serialNumber: String,
        model: String,
        name: String,
        phoneNumber: String?,
        aadhaarNumber: String?,
        amount: Double,
        description: String,
        dateInMillis: Long,
        quantity: Int,
        photoUri: String?,
        userId: String,
        technicianName: String,
        repairReason: String
    ): Boolean {
        val item = InventoryItem(
            id = UUID.randomUUID().toString(),
            serialNumber = serialNumber,
            model = model,
            name = name,
            phoneNumber = phoneNumber,
            aadhaarNumber = aadhaarNumber,
            amount = amount,
            description = description,
            dateInMillis = dateInMillis,
            quantity = quantity,
            photoUri = photoUri,
            isUnderRepair = true,
            technicianName = technicianName,
            repairReason = repairReason
        )
        db.collection("inventory_items").document(item.id).set(item).await()

        val history = HistoryEvent(
            id = UUID.randomUUID().toString(),
            actionType = "REPAIR_SENT",
            serialNumber = serialNumber,
            model = model,
            name = name,
            phoneNumber = phoneNumber,
            aadhaarNumber = aadhaarNumber,
            amount = amount,
            description = description,
            dateInMillis = dateInMillis,
            quantity = quantity,
            photoUri = photoUri,
            userId = userId,
            extraDetails = "Technician: $technicianName, Reason: $repairReason"
        )
        db.collection("history_events").document(history.id).set(history).await()
        processPartyTransactionIfApplicable(name, "REPAIR_SENT", amount * quantity, history.id)
        return true
    }

    suspend fun sendItemToRepair(itemId: String, technicianName: String, reason: String, userId: String): Boolean {
        val existing = getItemById(itemId) ?: return false
        val updated = existing.copy(
            isUnderRepair = true,
            technicianName = technicianName,
            repairReason = reason
        )
        db.collection("inventory_items").document(itemId).set(updated).await()

        val history = HistoryEvent(
            id = UUID.randomUUID().toString(),
            actionType = "REPAIR_SENT",
            serialNumber = existing.serialNumber,
            model = existing.model,
            name = existing.name,
            phoneNumber = existing.phoneNumber,
            aadhaarNumber = existing.aadhaarNumber,
            amount = existing.amount,
            description = existing.description,
            dateInMillis = System.currentTimeMillis(),
            quantity = existing.quantity,
            photoUri = existing.photoUri,
            userId = userId,
            extraDetails = "Technician: $technicianName, Reason: $reason"
        )
        db.collection("history_events").document(history.id).set(history).await()
        return true
    }

    suspend fun returnItemFromRepair(itemId: String, userId: String): Boolean {
        val existing = getItemById(itemId) ?: return false
        val updated = existing.copy(
            isUnderRepair = false,
            technicianName = null,
            repairReason = null
        )
        db.collection("inventory_items").document(itemId).set(updated).await()

        val history = HistoryEvent(
            id = UUID.randomUUID().toString(),
            actionType = "REPAIR_RETURNED",
            serialNumber = existing.serialNumber,
            model = existing.model,
            name = existing.name,
            phoneNumber = existing.phoneNumber,
            aadhaarNumber = existing.aadhaarNumber,
            amount = existing.amount,
            description = "Returned from repair: " + (existing.repairReason ?: ""),
            dateInMillis = System.currentTimeMillis(),
            quantity = existing.quantity,
            photoUri = existing.photoUri,
            userId = userId,
            extraDetails = "Technician responsible was: " + (existing.technicianName ?: "Unknown")
        )
        db.collection("history_events").document(history.id).set(history).await()
        return true
    }

    suspend fun updateInventoryItem(item: InventoryItem, userId: String): Boolean {
        db.collection("inventory_items").document(item.id).set(item).await()

        val history = HistoryEvent(
            id = UUID.randomUUID().toString(),
            actionType = "EDIT",
            serialNumber = item.serialNumber,
            model = item.model,
            name = item.name,
            phoneNumber = item.phoneNumber,
            aadhaarNumber = item.aadhaarNumber,
            amount = item.amount,
            description = "Edited item details: " + item.description,
            dateInMillis = System.currentTimeMillis(),
            quantity = item.quantity,
            photoUri = item.photoUri,
            userId = userId
        )
        db.collection("history_events").document(history.id).set(history).await()
        return true
    }

    suspend fun deleteInventoryItem(itemId: String, userId: String): Boolean {
        val existing = getItemById(itemId) ?: return false
        db.collection("inventory_items").document(itemId).delete().await()

        val history = HistoryEvent(
            id = UUID.randomUUID().toString(),
            actionType = "DELETE",
            serialNumber = existing.serialNumber,
            model = existing.model,
            name = existing.name,
            phoneNumber = existing.phoneNumber,
            aadhaarNumber = existing.aadhaarNumber,
            amount = existing.amount,
            description = "Deleted item from active inventory",
            dateInMillis = System.currentTimeMillis(),
            quantity = existing.quantity,
            photoUri = existing.photoUri,
            userId = userId
        )
        db.collection("history_events").document(history.id).set(history).await()
        return true
    }

    suspend fun addParty(party: com.example.data.model.Party) {
        db.collection("parties").document(party.id).set(party).await()
    }

    suspend fun editParty(partyId: String, name: String, phone: String, aadhaar: String) {
        val updates = mapOf(
            "name" to name,
            "phoneNumber" to phone,
            "aadhaarNumber" to aadhaar
        )
        db.collection("parties").document(partyId).update(updates).await()
    }

    suspend fun deleteParty(partyId: String) {
        db.collection("parties").document(partyId).delete().await()
    }

    suspend fun updatePartyBalance(partyId: String, amountDelta: Double) {
        val snap = db.collection("parties").document(partyId).get().await()
        val party = snap.toObject(com.example.data.model.Party::class.java)
        if (party != null) {
            val updated = party.copy(balance = party.balance + amountDelta)
            db.collection("parties").document(partyId).set(updated).await()
        }
    }

    suspend fun addLedgerEntry(entry: com.example.data.model.LedgerEntry) {
        db.collection("ledger_entries").document(entry.id).set(entry).await()
        if (entry.type == "PAYMENT_IN") {
            updatePartyBalance(entry.partyId, -entry.amount) // they pay us, balance down
        } else if (entry.type == "PAYMENT_OUT") {
            updatePartyBalance(entry.partyId, entry.amount) // we pay them, balance up (they owe us less, wait. If we pay them, we owe them less. Since balance = they owe us, negative means we owe them. If we pay them, balance goes UP towards 0). Yes, +entry.amount.
        }
    }

    suspend fun processPartyTransactionIfApplicable(name: String, type: String, amount: Double, historyEventId: String? = null) {
        val match = db.collection("parties").whereEqualTo("name", name).limit(1).get().await()
        val party = match.documents.firstOrNull()?.toObject(com.example.data.model.Party::class.java)
        if (party != null) {
            val entry = com.example.data.model.LedgerEntry(
                partyId = party.id,
                amount = amount,
                type = type,
                historyEventId = historyEventId
            )
            db.collection("ledger_entries").document(entry.id).set(entry).await()
            val delta = when (type) {
                "SALE" -> amount // they owe us
                "PURCHASE" -> -amount // we owe them
                "RETURN" -> -amount // we owe them for returned goods, or they owe us less. Wait, if a customer returns goods, we owe them money, so balance decreases. Yes, -amount.
                "REPAIR_SENT" -> amount // they owe us for repair fees? Or we owe them? Usually we charge for repair. 
                else -> 0.0
            }
            updatePartyBalance(party.id, delta)
        }
    }

    fun searchHistory(imei: String): Flow<List<HistoryEvent>> {
        return if (imei.isBlank()) {
            allHistoryEvents
        } else {
            callbackFlow {
                val sub = db.collection("history_events").whereEqualTo("serialNumber", imei).addSnapshotListener { snap, err ->
                    if (snap != null) {
                        trySend(snap.documents.mapNotNull { it.toObject(HistoryEvent::class.java) })
                    }
                }
                awaitClose { sub.remove() }
            }
        }
    }

    // --- Cloud-Synced Attendance Systems ---
    suspend fun insertAttendanceRecord(record: AttendanceRecord) {
        db.collection("attendance_records").document(record.id).set(record).await()
    }

    suspend fun updateAttendanceRecord(record: AttendanceRecord) {
        db.collection("attendance_records").document(record.id).set(record).await()
    }

    suspend fun insertLeaveApplication(leave: LeaveApplication) {
        db.collection("leave_applications").document(leave.id).set(leave).await()
    }

    suspend fun updateLeaveApplication(leave: LeaveApplication) {
        db.collection("leave_applications").document(leave.id).set(leave).await()
    }

    suspend fun insertNotification(notification: NotificationLog) {
        db.collection("attendance_notifications").document(notification.id).set(notification).await()
    }
}
