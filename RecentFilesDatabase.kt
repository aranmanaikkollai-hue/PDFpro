package com.propdf.editor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecentFileEntity::class], version = 1, exportSchema = false)
abstract class RecentFilesDatabase : RoomDatabase() {

    abstract fun recentFilesDao(): RecentFilesDao

    companion object {
        @Volatile
        private var INSTANCE: RecentFilesDatabase? = null

        fun get(context: Context): RecentFilesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecentFilesDatabase::class.java,
                    "recent_files_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
