package com.propdf.editor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFilesDao {

    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC")
    fun getAllFiles(): Flow<List<RecentFileEntity>>

    @Query("SELECT * FROM recent_files ORDER BY lastOpened DESC LIMIT :limit")
    fun getRecentFiles(limit: Int = 20): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecentFileEntity)

    @Delete
    suspend fun delete(entity: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM recent_files")
    suspend fun deleteAll()

    @Query("SELECT * FROM recent_files WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): RecentFileEntity?
}
