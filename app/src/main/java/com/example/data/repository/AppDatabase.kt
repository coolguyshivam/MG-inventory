package com.example.data.repository

import androidx.room.*
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import com.example.data.model.Employee
import com.example.data.model.AttendanceRecord
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
    @Query("SELECT * FROM inventory_items ORDER BY updatedTimestamp DESC")
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

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees ORDER BY name ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Delete
    suspend fun deleteEmployee(employee: Employee)

    @Query("DELETE FROM employees")
    suspend fun clearAll()
}

@Dao
interface AttendanceRecordDao {
    @Query("SELECT * FROM attendance_records ORDER BY date DESC, timestamp DESC")
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE date = :date")
    fun getAttendanceForDate(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE employeeId = :employeeId ORDER BY date DESC")
    fun getAttendanceForEmployee(employeeId: Int): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecord(record: AttendanceRecord): Long

    @Update
    suspend fun updateAttendanceRecord(record: AttendanceRecord)

    @Delete
    suspend fun deleteAttendanceRecord(record: AttendanceRecord)

    @Query("DELETE FROM attendance_records WHERE id = :id")
    suspend fun deleteAttendanceRecordById(id: Int)

    @Query("DELETE FROM attendance_records")
    suspend fun clearAll()
}

@Database(entities = [HistoryEvent::class, InventoryItem::class, Employee::class, AttendanceRecord::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract val historyEventDao: HistoryEventDao
    abstract val inventoryItemDao: InventoryItemDao
    abstract val employeeDao: EmployeeDao
    abstract val attendanceRecordDao: AttendanceRecordDao

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
