package com.example.data.repository

import com.example.data.database.HistoryDao
import com.example.data.database.InventoryDao
import com.example.data.database.UserDao
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class InventoryRepository(
    private val inventoryDao: InventoryDao,
    private val historyDao: HistoryDao,
    private val userDao: UserDao
) {
    // Collect reactive streams
    val allInventoryItems: Flow<List<InventoryItem>> = inventoryDao.getAllItemsFlow()
    val allHistoryEvents: Flow<List<HistoryEvent>> = historyDao.getAllEventsFlow()
    val allUsers: Flow<List<User>> = userDao.getAllUsers()

    suspend fun getUserCount(): Int = userDao.getUserCount()
    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)
    suspend fun insertUser(user: User) = userDao.insertUser(user)
    suspend fun deleteUser(user: User) = userDao.deleteUser(user)
    suspend fun updateUser(user: User) = userDao.updateUser(user)

    // Fetch single item
    suspend fun getItemBySerialNumber(serialNumber: String): InventoryItem? {
        return inventoryDao.getItemBySerialNumber(serialNumber)
    }

    suspend fun getItemById(id: Int): InventoryItem? {
        return inventoryDao.getItemById(id)
    }

    // Purchase - adds a brand new item to inventory & registers a history event
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
        // Create inventory item
        val item = InventoryItem(
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
        val rowId = inventoryDao.insertItem(item)

        // Generate History Event
        if (rowId > 0) {
            val history = HistoryEvent(
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
            historyDao.insertEvent(history)
            return true
        }
        return false
    }

    // Sale - removes item from local inventory list & records a history event
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
        // Find existing product in inventory to remove or update quantity
        val existing = inventoryDao.getItemBySerialNumber(serialNumber)
        if (existing != null) {
            if (existing.quantity <= quantity) {
                // Remove from inventory
                inventoryDao.deleteItem(existing)
            } else {
                // Decrement inventory quantity
                inventoryDao.updateItem(existing.copy(quantity = existing.quantity - quantity))
            }
        }

        // Add history event for the sale
        val history = HistoryEvent(
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
        val eventId = historyDao.insertEvent(history)
        return eventId > 0
    }

    // Return - adds an item back structure and records return event
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
        val existing = inventoryDao.getItemBySerialNumber(serialNumber)
        if (existing != null) {
            inventoryDao.updateItem(existing.copy(quantity = existing.quantity + quantity))
        } else {
            val item = InventoryItem(
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
            inventoryDao.insertItem(item)
        }

        // Add event to history
        val history = HistoryEvent(
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
        return historyDao.insertEvent(history) > 0
    }

    // Repair via Transactions form (registers directly)
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
        // Adds item directly to inventory marked as under repair
        val item = InventoryItem(
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
        inventoryDao.insertItem(item)

        // Event
        val history = HistoryEvent(
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
        return historyDao.insertEvent(history) > 0
    }

    // Send existing inventory item to Repair
    suspend fun sendItemToRepair(itemId: Int, technicianName: String, reason: String, userId: String): Boolean {
        val existing = inventoryDao.getItemById(itemId) ?: return false
        val updated = existing.copy(
            isUnderRepair = true,
            technicianName = technicianName,
            repairReason = reason
        )
        inventoryDao.updateItem(updated)

        // History
        val history = HistoryEvent(
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
        historyDao.insertEvent(history)
        return true
    }

    // Return item from repair back to main inventory
    suspend fun returnItemFromRepair(itemId: Int, userId: String): Boolean {
        val existing = inventoryDao.getItemById(itemId) ?: return false
        val updated = existing.copy(
            isUnderRepair = false,
            technicianName = null,
            repairReason = null
        )
        inventoryDao.updateItem(updated)

        // History
        val history = HistoryEvent(
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
        historyDao.insertEvent(history)
        return true
    }

    // Edit item details
    suspend fun updateInventoryItem(item: InventoryItem, userId: String): Boolean {
        inventoryDao.updateItem(item)

        // Save a history event of the edit
        val history = HistoryEvent(
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
        historyDao.insertEvent(history)
        return true
    }

    // Delete item details
    suspend fun deleteInventoryItem(itemId: Int, userId: String): Boolean {
        val existing = inventoryDao.getItemById(itemId) ?: return false
        inventoryDao.deleteItem(existing)

        // Add to history stream
        val history = HistoryEvent(
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
        historyDao.insertEvent(history)
        return true
    }

    // Get filtered history flow
    fun searchHistory(imei: String): Flow<List<HistoryEvent>> {
        return if (imei.isBlank()) {
            allHistoryEvents
        } else {
            historyDao.getEventsByImeiFlow(imei)
        }
    }
}
