package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [CachedSheetRowEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class SmartSheetDatabase : RoomDatabase() {
  abstract fun cachedSheetRowDao(): CachedSheetRowDao

  companion object {
    @Volatile private var instance: SmartSheetDatabase? = null

    fun getInstance(context: Context): SmartSheetDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                SmartSheetDatabase::class.java,
                "smart_sheet_cache.db",
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { instance = it }
        }
    }
  }
}
