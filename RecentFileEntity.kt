package com.propdf.editor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for recently opened PDF files.
 */
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
