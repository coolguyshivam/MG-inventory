package com.example.data.database

import androidx.room.*
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY dateInMillis DESC")
    fun getAllItemsFlow(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE serialNumber = :serialNumber LIMIT 1")
    suspend fun getItemBySerialNumber(serialNumber: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_events ORDER BY timestamp DESC")
    fun getAllEventsFlow(): Flow<List<HistoryEvent>>

    @Query("SELECT * FROM history_events WHERE serialNumber LIKE '%' || :imei || '%' ORDER BY timestamp DESC")
    fun getEventsByImeiFlow(imei: String): Flow<List<HistoryEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: HistoryEvent): Long
}
