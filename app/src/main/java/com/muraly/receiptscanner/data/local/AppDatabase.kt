package com.muraly.receiptscanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.muraly.receiptscanner.data.local.dao.ReceiptDao
import com.muraly.receiptscanner.data.local.entity.ReceiptEntity
import com.muraly.receiptscanner.data.local.entity.ReceiptItemEntity

@Database(entities = [ReceiptEntity::class, ReceiptItemEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "receipt_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
