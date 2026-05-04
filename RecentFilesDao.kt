package com.propdf.editor.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFilesDao {
    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC")
    fun getAll(): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE isFavourite = 1 ORDER BY lastOpened DESC")
    fun getFavourites(): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE category = :category ORDER BY lastOpened DESC")
    fun getByCategory(category: String): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files WHERE name LIKE :query ORDER BY lastOpened DESC")
    fun search(query: String): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM recent_files")
    suspend fun deleteAll()

    @Query("DELETE FROM recent_files WHERE isFavourite = 0")
    suspend fun deleteNonFavourites()

    @Query("UPDATE recent_files SET isFavourite = :isFavourite WHERE uri = :uri")
    suspend fun updateFavourite(uri: String, isFavourite: Boolean)

    @Query("UPDATE recent_files SET category = :category WHERE uri = :uri")
    suspend fun updateCategory(uri: String, category: String)

    @Query("UPDATE recent_files SET pageCount = :pageCount WHERE uri = :uri")
    suspend fun updatePageCount(uri: String, pageCount: Int)

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): RecentFileEntity?

    @Query("SELECT COUNT(*) FROM recent_files")
    suspend fun count(): Int
}
