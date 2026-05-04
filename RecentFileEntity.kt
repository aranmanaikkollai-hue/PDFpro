package com.propdf.editor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val path: String,
    val uri: String,
    val size: Long,
    val lastOpened: Long,
    val pageCount: Int = 0,
    val category: String = "document"
)
