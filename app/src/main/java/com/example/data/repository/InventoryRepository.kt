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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import java.util.UUID

class InventoryRepository {
    private val db by lazy { FirebaseFirestore.getInstance() }

    val allInventoryItems: Flow<List<InventoryItem>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_INVENTORY_ITEMS).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(InventoryItem::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allHistoryEvents: Flow<List<HistoryEvent>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_HISTORY_EVENTS).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(HistoryEvent::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allUsers: Flow<List<User>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_USERS).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(User::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allParties: Flow<List<com.example.data.model.Party>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_PARTIES).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(com.example.data.model.Party::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allLedgerEntries: Flow<List<com.example.data.model.LedgerEntry>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_LEDGER_ENTRIES).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(com.example.data.model.LedgerEntry::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allAttendanceRecords: Flow<List<AttendanceRecord>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_ATTENDANCE_RECORDS).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(AttendanceRecord::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allLeaveApplications: Flow<List<LeaveApplication>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_LEAVE_APPLICATIONS).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(LeaveApplication::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    val allNotifications: Flow<List<NotificationLog>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_ATTENDANCE_NOTIFICATIONS).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(NotificationLog::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }

    private val localAddedItems = MutableStateFlow<Map<String, com.example.data.model.BrandStockItem>>(emptyMap())
    private val localDeletedItems = MutableStateFlow<Set<String>>(emptySet())

    private val localAddedTransactions = MutableStateFlow<Map<String, com.example.data.model.BrandStockTransaction>>(emptyMap())
    private val localDeletedTransactions = MutableStateFlow<Set<String>>(emptySet())

    val allBrandStockItems: Flow<List<com.example.data.model.BrandStockItem>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_ITEMS).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(com.example.data.model.BrandStockItem::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }.combine(localAddedItems) { firestoreList, addedMap ->
        firestoreList + addedMap.values
    }.combine(localDeletedItems) { list, deletedIds ->
        list.filter { it.id !in deletedIds }.distinctBy { it.id }
    }

    val allBrandStockTransactions: Flow<List<com.example.data.model.BrandStockTransaction>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_TRANSACTIONS).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(com.example.data.model.BrandStockTransaction::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }.combine(localAddedTransactions) { firestoreList, addedMap ->
        firestoreList + addedMap.values
    }.combine(localDeletedTransactions) { list, deletedIds ->
        list.filter { it.id !in deletedIds }.distinctBy { it.id }
    }

    private val fallbackBrandVariants = MutableStateFlow<List<com.example.data.model.BrandVariant>>(emptyList())

    val allBrandVariants: Flow<List<com.example.data.model.BrandVariant>> = callbackFlow {
        val sub = db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_VARIANTS).addSnapshotListener { snap, err ->
            if (snap != null) {
                trySend(snap.documents.mapNotNull { it.toObject(com.example.data.model.BrandVariant::class.java) })
            }
        }
        awaitClose { sub.remove() }
    }.combine(fallbackBrandVariants) { firestoreList, localList ->
        (firestoreList + localList).distinctBy { it.id }
    }

    suspend fun getBrandStockItemByImei(imei: String): com.example.data.model.BrandStockItem? {
        val cleaned = imei.trim()
        if (cleaned.isBlank()) return null
        return try {
            val query = db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_ITEMS)
                .whereEqualTo("imei", cleaned)
                .limit(1)
                .get()
                .await()
            query.documents.firstOrNull()?.toObject(com.example.data.model.BrandStockItem::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addBrandStock(item: com.example.data.model.BrandStockItem, transaction: com.example.data.model.BrandStockTransaction): Boolean {
        // Optimistic local update
        localAddedItems.value = localAddedItems.value + (item.id to item)
        localDeletedItems.value = localDeletedItems.value - item.id
        
        localAddedTransactions.value = localAddedTransactions.value + (transaction.id to transaction)
        localDeletedTransactions.value = localDeletedTransactions.value - transaction.id

        return try {
            val existing = getBrandStockItemByImei(item.imei)
            if (existing != null) {
                // If it already existed, revert our local optimistic updates and return false
                localAddedItems.value = localAddedItems.value - item.id
                localAddedTransactions.value = localAddedTransactions.value - transaction.id
                return false // IMEI must be unique in active inventory
            }
            
            // Atomic batch commit for stock item and transaction history log
            val batch = db.batch()
            val itemRef = db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_ITEMS).document(item.id)
            val txRef = db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_TRANSACTIONS).document(transaction.id)
            batch.set(itemRef, item)
            batch.set(txRef, transaction)
            batch.commit().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Roll back local state on error
            localAddedItems.value = localAddedItems.value - item.id
            localAddedTransactions.value = localAddedTransactions.value - transaction.id
            false
        }
    }

    suspend fun sellBrandStock(imei: String, warehouse: String, operator: String, date: Long, notes: String? = null): Boolean {
        var existingItem: com.example.data.model.BrandStockItem? = null
        var txCreated: com.example.data.model.BrandStockTransaction? = null
        return try {
            val existing = localAddedItems.value.values.firstOrNull { it.imei == imei } ?: getBrandStockItemByImei(imei) ?: return false // Not found
            existingItem = existing
            
            // Optimistic update
            localDeletedItems.value = localDeletedItems.value + existing.id
            localAddedItems.value = localAddedItems.value - existing.id
            
            val tx = com.example.data.model.BrandStockTransaction(
                id = UUID.randomUUID().toString(),
                imei = existing.imei,
                brand = existing.brand,
                variant = existing.variant,
                color = existing.color,
                warehouse = warehouse, // The actual warehouse sold from
                type = "OUT",
                dateInMillis = date,
                operator = operator,
                notes = notes
            )
            txCreated = tx
            localAddedTransactions.value = localAddedTransactions.value + (tx.id to tx)
            localDeletedTransactions.value = localDeletedTransactions.value - tx.id

            // Concurrency-safe atomic transaction to prevent double-selling across multiple counters
            val itemRef = db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_ITEMS).document(existing.id)
            val txRef = db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_TRANSACTIONS).document(tx.id)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(itemRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Brand stock item already sold by another counter staff.")
                }
                transaction.delete(itemRef)
                transaction.set(txRef, tx)
            }.await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Roll back local optimistic changes on failure
            existingItem?.let { existing ->
                localDeletedItems.value = localDeletedItems.value - existing.id
            }
            txCreated?.let { tx ->
                localAddedTransactions.value = localAddedTransactions.value - tx.id
            }
            false
        }
    }

    suspend fun transferBrandStock(imei: String, toWarehouse: String, operator: String, date: Long, notes: String? = null): Boolean {
        return try {
            val query = db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_ITEMS)
                .whereEqualTo("imei", imei.trim())
                .limit(1)
                .get()
                .await()
            val existingDoc = query.documents.firstOrNull() ?: return false
            val itemRef = existingDoc.reference
            val currentItem = existingDoc.toObject(com.example.data.model.BrandStockItem::class.java) ?: return false

            val tx = com.example.data.model.BrandStockTransaction(
                id = UUID.randomUUID().toString(),
                imei = currentItem.imei,
                brand = currentItem.brand,
                variant = currentItem.variant,
                color = currentItem.color,
                warehouse = toWarehouse,
                type = "TRANSFER",
                dateInMillis = date,
                operator = operator,
                notes = notes ?: "Transferred to $toWarehouse"
            )
            val txRef = db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_TRANSACTIONS).document(tx.id)

            db.runTransaction { transaction ->
                val snap = transaction.get(itemRef)
                if (!snap.exists()) {
                    throw IllegalStateException("Brand stock item no longer available for transfer.")
                }
                transaction.update(itemRef, "warehouse", toWarehouse)
                transaction.set(txRef, tx)
            }.await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun addBrandVariant(variant: com.example.data.model.BrandVariant): Boolean {
        fallbackBrandVariants.value = fallbackBrandVariants.value + variant
        return try {
            db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_VARIANTS)
                .document(variant.id)
                .set(variant)
                .await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            true
        }
    }

    suspend fun deleteBrandVariant(id: String): Boolean {
        return try {
            db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_VARIANTS)
                .document(id)
                .delete()
                .await()
            fallbackBrandVariants.value = fallbackBrandVariants.value.filter { it.id != id }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteBrandStockItem(id: String): Boolean {
        return try {
            db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_ITEMS)
                .document(id)
                .delete()
                .await()
            localDeletedItems.value = localDeletedItems.value + id
            localAddedItems.value = localAddedItems.value - id
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteBrandTransaction(id: String): Boolean {
        return try {
            db.collection(com.example.data.cloud.AppCloudConfig.COLL_BRAND_STOCK_TRANSACTIONS)
                .document(id)
                .delete()
                .await()
            localDeletedTransactions.value = localDeletedTransactions.value + id
            localAddedTransactions.value = localAddedTransactions.value - id
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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

    suspend fun getLastSaleEventBySerialNumber(serialNumber: String): HistoryEvent? {
        return try {
            val snap = db.collection("history_events")
                .whereEqualTo("actionType", "SALE")
                .whereEqualTo("serialNumber", serialNumber)
                .get()
                .await()
            snap.documents
                .mapNotNull { it.toObject(HistoryEvent::class.java) }
                .maxByOrNull { it.timestamp }
        } catch (e: Exception) {
            null
        }
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
        userId: String,
        salePrice: Double = 0.0,
        minSalePrice: Double = 0.0
    ): Boolean {
        val uploadedPhotoUri = com.example.util.AppUtils.processAndUploadPhotos(photoUri)
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
            photoUri = uploadedPhotoUri,
            underRepair = false,
            salePrice = salePrice,
            minSalePrice = minSalePrice
        )
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
            photoUri = uploadedPhotoUri,
            userId = userId
        )

        // Atomic write batch ensuring item and audit log commit together
        val batch = db.batch()
        val itemRef = db.collection("inventory_items").document(item.id)
        val histRef = db.collection("history_events").document(history.id)
        batch.set(itemRef, item)
        batch.set(histRef, history)
        batch.commit().await()
        
        if (uploadedPhotoUri != null && uploadedPhotoUri.contains("file://")) {
            com.example.util.AppUtils.uploadPhotoInBackground(item.id, uploadedPhotoUri, "inventory_items")
            com.example.util.AppUtils.uploadPhotoInBackground(history.id, uploadedPhotoUri, "history_events")
        }

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
        // Query item reference by serial number
        val querySnap = db.collection("inventory_items")
            .whereEqualTo("serialNumber", serialNumber.trim())
            .limit(1)
            .get()
            .await()
        val existingDoc = querySnap.documents.firstOrNull()
        val existingItem = existingDoc?.toObject(InventoryItem::class.java)

        val uploadedPhotoUri = com.example.util.AppUtils.processAndUploadPhotos(photoUri ?: existingItem?.photoUri)
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
            photoUri = uploadedPhotoUri,
            userId = userId
        )

        if (existingDoc != null) {
            val docRef = existingDoc.reference
            // Concurrency-safe atomic transaction preventing double-selling across ~20 staff counters
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Device $serialNumber has already been sold by another staff member.")
                }
                val currentQty = snapshot.getLong("quantity")?.toInt() ?: 1
                if (currentQty < quantity) {
                    throw IllegalStateException("Insufficient inventory: Only $currentQty units left in stock.")
                }
                if (currentQty <= quantity) {
                    transaction.delete(docRef)
                } else {
                    transaction.update(docRef, "quantity", currentQty - quantity)
                }
                val histRef = db.collection("history_events").document(history.id)
                transaction.set(histRef, history)
            }.await()
        } else {
            // Unstocked or accessory direct sale
            val histRef = db.collection("history_events").document(history.id)
            histRef.set(history).await()
        }
        
        if (uploadedPhotoUri != null && uploadedPhotoUri.contains("file://")) {
            com.example.util.AppUtils.uploadPhotoInBackground(history.id, uploadedPhotoUri, "history_events")
        }

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
        val uploadedPhotoUri = com.example.util.AppUtils.processAndUploadPhotos(photoUri)
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
                photoUri = uploadedPhotoUri,
                underRepair = false
            )
            db.collection("inventory_items").document(item.id).set(item).await()
            if (uploadedPhotoUri != null && uploadedPhotoUri.contains("file://")) {
                com.example.util.AppUtils.uploadPhotoInBackground(item.id, uploadedPhotoUri, "inventory_items")
            }
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
            photoUri = uploadedPhotoUri,
            userId = userId
        )
        db.collection("history_events").document(history.id).set(history).await()
        
        if (uploadedPhotoUri != null && uploadedPhotoUri.contains("file://")) {
            com.example.util.AppUtils.uploadPhotoInBackground(history.id, uploadedPhotoUri, "history_events")
        }

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
        val uploadedPhotoUri = com.example.util.AppUtils.processAndUploadPhotos(photoUri)
        val existing = getItemBySerialNumber(serialNumber)
        val repairCost = amount
        val newAmount = (existing?.amount ?: 0.0) + repairCost
        val item = if (existing != null) {
            existing.copy(
                amount = newAmount,
                underRepair = true,
                technicianName = technicianName,
                repairReason = repairReason,
                phoneNumber = phoneNumber ?: existing.phoneNumber,
                aadhaarNumber = aadhaarNumber ?: existing.aadhaarNumber,
                description = if (description.isNotBlank()) description else existing.description,
                photoUri = uploadedPhotoUri ?: existing.photoUri,
                lastUpdated = System.currentTimeMillis()
            )
        } else {
            InventoryItem(
                id = UUID.randomUUID().toString(),
                serialNumber = serialNumber,
                model = model,
                name = name,
                phoneNumber = phoneNumber,
                aadhaarNumber = aadhaarNumber,
                amount = newAmount,
                description = description,
                dateInMillis = dateInMillis,
                quantity = quantity,
                photoUri = uploadedPhotoUri,
                underRepair = true,
                technicianName = technicianName,
                repairReason = repairReason
            )
        }
        db.collection("inventory_items").document(item.id).set(item).await()

        val history = HistoryEvent(
            id = UUID.randomUUID().toString(),
            actionType = "REPAIR_SENT",
            serialNumber = serialNumber,
            model = model,
            name = name,
            phoneNumber = phoneNumber,
            aadhaarNumber = aadhaarNumber,
            amount = repairCost, // History card shows repair cost only on the outside
            description = description,
            dateInMillis = dateInMillis,
            quantity = quantity,
            photoUri = uploadedPhotoUri ?: item.photoUri,
            userId = userId,
            extraDetails = "Total Phone Cost: ₹${String.format("%,.2f", newAmount)}, Technician: $technicianName, Reason: $repairReason, Repair Cost: ₹$repairCost"
        )
        db.collection("history_events").document(history.id).set(history).await()
        
        if (uploadedPhotoUri != null && uploadedPhotoUri.contains("file://")) {
            com.example.util.AppUtils.uploadPhotoInBackground(item.id, uploadedPhotoUri, "inventory_items")
            com.example.util.AppUtils.uploadPhotoInBackground(history.id, uploadedPhotoUri, "history_events")
        }

        processPartyTransactionIfApplicable(name, "REPAIR_SENT", repairCost * quantity, history.id)
        return true
    }

    suspend fun sendItemToRepair(itemId: String, technicianName: String, reason: String, userId: String): Boolean {
        val existing = getItemById(itemId) ?: return false
        val updated = existing.copy(
            underRepair = true,
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
            amount = 0.0,
            description = existing.description,
            dateInMillis = System.currentTimeMillis(),
            quantity = existing.quantity,
            photoUri = existing.photoUri,
            userId = userId,
            extraDetails = "Total Phone Cost: ₹${String.format("%,.2f", existing.amount)}, Technician: $technicianName, Reason: $reason"
        )
        db.collection("history_events").document(history.id).set(history).await()
        return true
    }

    suspend fun returnItemFromRepair(itemId: String, userId: String, repairCost: Double = 0.0): Boolean {
        val existing = getItemById(itemId) ?: return false
        val newAmount = existing.amount + repairCost
        val updated = existing.copy(
            underRepair = false,
            technicianName = null,
            repairReason = null,
            amount = newAmount
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
            amount = repairCost,
            description = if (repairCost > 0.0) {
                "Returned from repair: " + (existing.repairReason ?: "") + " (Repair Cost: ₹$repairCost added to phone total cost)"
            } else {
                "Returned from repair: " + (existing.repairReason ?: "")
            },
            dateInMillis = System.currentTimeMillis(),
            quantity = existing.quantity,
            photoUri = existing.photoUri,
            userId = userId,
            extraDetails = "Total Phone Cost: ₹${String.format("%,.2f", newAmount)}, Technician: " + (existing.technicianName ?: "Unknown") + if (repairCost > 0.0) ", Repair Cost: ₹$repairCost" else ""
        )
        db.collection("history_events").document(history.id).set(history).await()
        return true
    }

    suspend fun updateInventoryItem(item: InventoryItem, userId: String): Boolean {
        val uploadedPhotoUri = com.example.util.AppUtils.processAndUploadPhotos(item.photoUri)
        val finalItem = item.copy(photoUri = uploadedPhotoUri)
        db.collection("inventory_items").document(finalItem.id).set(finalItem).await()

        val history = HistoryEvent(
            id = UUID.randomUUID().toString(),
            actionType = "EDIT",
            serialNumber = finalItem.serialNumber,
            model = finalItem.model,
            name = finalItem.name,
            phoneNumber = finalItem.phoneNumber,
            aadhaarNumber = finalItem.aadhaarNumber,
            amount = finalItem.amount,
            description = "Edited item details: " + finalItem.description,
            dateInMillis = System.currentTimeMillis(),
            quantity = finalItem.quantity,
            photoUri = finalItem.photoUri,
            userId = userId
        )
        db.collection("history_events").document(history.id).set(history).await()
        
        if (uploadedPhotoUri != null && uploadedPhotoUri.contains("file://")) {
            com.example.util.AppUtils.uploadPhotoInBackground(finalItem.id, uploadedPhotoUri, "inventory_items")
            com.example.util.AppUtils.uploadPhotoInBackground(history.id, uploadedPhotoUri, "history_events")
        }

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

    suspend fun editParty(partyId: String, name: String, phone: String, aadhaar: String, address: String = "") {
        val updates = mapOf(
            "name" to name,
            "phoneNumber" to phone,
            "aadhaarNumber" to aadhaar,
            "address" to address
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
        val finalRecord = record.copy(
            checkInSelfieBase64 = record.checkInSelfieBase64?.let { com.example.util.AppUtils.uploadPhotoToFirebaseStorage(it) },
            checkOutSelfieBase64 = record.checkOutSelfieBase64?.let { com.example.util.AppUtils.uploadPhotoToFirebaseStorage(it) }
        )
        db.collection("attendance_records").document(finalRecord.id).set(finalRecord).await()
    }

    suspend fun updateAttendanceRecord(record: AttendanceRecord) {
        val finalRecord = record.copy(
            checkInSelfieBase64 = record.checkInSelfieBase64?.let { com.example.util.AppUtils.uploadPhotoToFirebaseStorage(it) },
            checkOutSelfieBase64 = record.checkOutSelfieBase64?.let { com.example.util.AppUtils.uploadPhotoToFirebaseStorage(it) }
        )
        db.collection("attendance_records").document(finalRecord.id).set(finalRecord).await()
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
