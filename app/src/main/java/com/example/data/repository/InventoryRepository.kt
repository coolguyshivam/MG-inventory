package com.example.data.repository

import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val database: AppDatabase) {

    private val historyEventDao = database.historyEventDao
    private val inventoryItemDao = database.inventoryItemDao
    private val employeeDao = database.employeeDao
    private val attendanceRecordDao = database.attendanceRecordDao

    fun getAllEvents(): Flow<List<HistoryEvent>> = historyEventDao.getAllEvents()

    fun getAllInventoryItems(): Flow<List<InventoryItem>> = inventoryItemDao.getAllItems()

    // Employee Methods
    fun getAllEmployees(): Flow<List<com.example.data.model.Employee>> = employeeDao.getAllEmployees()

    suspend fun insertEmployee(employee: com.example.data.model.Employee): Long = employeeDao.insertEmployee(employee)

    suspend fun updateEmployee(employee: com.example.data.model.Employee) = employeeDao.updateEmployee(employee)

    suspend fun deleteEmployee(employee: com.example.data.model.Employee) = employeeDao.deleteEmployee(employee)

    // Attendance Methods
    fun getAllAttendanceRecords(): Flow<List<com.example.data.model.AttendanceRecord>> = attendanceRecordDao.getAllAttendanceRecords()

    fun getAttendanceForDate(date: String): Flow<List<com.example.data.model.AttendanceRecord>> = attendanceRecordDao.getAttendanceForDate(date)

    fun getAttendanceForEmployee(employeeId: Int): Flow<List<com.example.data.model.AttendanceRecord>> = attendanceRecordDao.getAttendanceForEmployee(employeeId)

    suspend fun insertAttendanceRecord(record: com.example.data.model.AttendanceRecord): Long = attendanceRecordDao.insertAttendanceRecord(record)

    suspend fun updateAttendanceRecord(record: com.example.data.model.AttendanceRecord) = attendanceRecordDao.updateAttendanceRecord(record)

    suspend fun deleteAttendanceRecord(record: com.example.data.model.AttendanceRecord) = attendanceRecordDao.deleteAttendanceRecord(record)

    suspend fun deleteAttendanceRecordById(id: Int) = attendanceRecordDao.deleteAttendanceRecordById(id)

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
        employeeDao.clearAll()
        attendanceRecordDao.clearAll()
    }
}
