package com.example.data.repository

import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val database: AppDatabase) {

    private val historyEventDao = database.historyEventDao
    private val inventoryItemDao = database.inventoryItemDao

    fun getAllEvents(): Flow<List<HistoryEvent>> = historyEventDao.getAllEvents()

    fun getAllInventoryItems(): Flow<List<InventoryItem>> = inventoryItemDao.getAllItems()

    suspend fun getItemBySerialNumber(serialNumber: String): InventoryItem? {
        return inventoryItemDao.getItemBySerialNumber(serialNumber)
    }

    suspend fun insertEvent(event: HistoryEvent): Long {
        return historyEventDao.insertEvent(event)
    }

    suspend fun updateEvent(event: HistoryEvent) {
        historyEventDao.updateEvent(event)
    }

    suspend fun deleteEventById(id: Int) {
        historyEventDao.deleteEventById(id)
    }

    suspend fun insertItem(item: InventoryItem): Long {
        return inventoryItemDao.insertItem(item)
    }

    suspend fun updateItem(item: InventoryItem) {
        inventoryItemDao.updateItem(item)
    }

    suspend fun deleteItemBySerial(serialNumber: String) {
        inventoryItemDao.deleteItemBySerial(serialNumber)
    }

    suspend fun clearAll() {
        historyEventDao.clearAll()
        inventoryItemDao.clearAll()
    }
}
