package com.example.data.repository

import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import com.example.data.model.User
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
}
