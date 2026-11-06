package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CachedSite::class], version = 1, exportSchema = false)
abstract class TowerCamDatabase : RoomDatabase() {

    abstract fun cachedSiteDao(): CachedSiteDao

    companion object {
        @Volatile
        private var INSTANCE: TowerCamDatabase? = null

        fun getInstance(context: Context): TowerCamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TowerCamDatabase::class.java,
                    "tower_cam_local_cache.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
