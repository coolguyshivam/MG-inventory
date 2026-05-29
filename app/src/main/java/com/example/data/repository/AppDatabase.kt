package com.example.data.repository

import androidx.room.*
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem

import kotlinx.coroutines.flow.Flow


@Dao
interface HistoryEventDao {
    @Query("SELECT * FROM history_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<HistoryEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: HistoryEvent): Long

    @Update
    suspend fun updateEvent(event: HistoryEvent)

    @Delete
    suspend fun deleteEvent(event: HistoryEvent)

    @Query("DELETE FROM history_events WHERE id = :id")
    suspend fun deleteEventById(id: Int)

    @Query("DELETE FROM history_events")
    suspend fun clearAll()
}

@Dao
interface InventoryItemDao {
    @Query("SELECT * FROM inventory_items ORDER BY dateInMillis DESC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE serialNumber = :serialNumber LIMIT 1")
    suspend fun getItemBySerialNumber(serialNumber: String): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE serialNumber = :serialNumber")
    suspend fun deleteItemBySerial(serialNumber: String)

    @Query("DELETE FROM inventory_items")
    suspend fun clearAll()
}

@Database(entities = [HistoryEvent::class, InventoryItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract val historyEventDao: HistoryEventDao
    abstract val inventoryItemDao: InventoryItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mobile_gallery_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
