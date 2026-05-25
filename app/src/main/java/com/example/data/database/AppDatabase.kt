package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import com.example.data.model.User

@Database(entities = [InventoryItem::class, HistoryEvent::class, User::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    abstract fun historyDao(): HistoryDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stock_inventory_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
