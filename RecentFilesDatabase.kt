package com.propdf.editor.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val fileSizeBytes: Long = 0L,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val isFavourite: Boolean = false,
    val category: String = "",
    val pageCount: Int = 0,
    val thumbnailPath: String = ""
)

@Dao
interface RecentFilesDao {
    @Query("SELECT * FROM recent_files ORDER BY lastOpenedAt DESC")
    fun getAll(): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE isFavourite = 1 ORDER BY lastOpenedAt DESC")
    fun getFavourites(): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE category = :category ORDER BY lastOpenedAt DESC")
    fun getByCategory(category: String): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE displayName LIKE :query ORDER BY lastOpenedAt DESC")
    fun search(query: String): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): RecentFileEntity?

    @Query("SELECT COUNT(*) FROM recent_files")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()

    @Query("DELETE FROM recent_files WHERE isFavourite = 0 AND category = ''")
    suspend fun clearRecentOnly()

    @Query("UPDATE recent_files SET isFavourite = :fav WHERE uri = :uri")
    suspend fun setFavourite(uri: String, fav: Boolean)

    @Query("UPDATE recent_files SET category = :cat WHERE uri = :uri")
    suspend fun setCategory(uri: String, cat: String)

    @Query("UPDATE recent_files SET pageCount = :count WHERE uri = :uri")
    suspend fun updatePageCount(uri: String, count: Int)
}

@Database(entities = [RecentFileEntity::class], version = 3, exportSchema = false)
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
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
