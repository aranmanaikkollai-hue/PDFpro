package com.propdf.editor.data.local

import android.content.Context
import androidx.room.*

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val path: String,
    val name: String,
    val size: Long,
    val lastOpened: Long
)

@Dao
interface RecentFilesDao {
    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC LIMIT 10")
    fun getRecentFiles(): List<RecentFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(file: RecentFileEntity)

    @Delete
    fun delete(file: RecentFileEntity)
}

@Database(entities = [RecentFileEntity::class], version = 1)
abstract class RecentFilesDatabase : RoomDatabase() {
    abstract fun recentFilesDao(): RecentFilesDao

    companion object {
        @Volatile
        private var INSTANCE: RecentFilesDatabase? = null

        fun get(context: Context): RecentFilesDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecentFilesDatabase::class.java,
                    "recent_files.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
