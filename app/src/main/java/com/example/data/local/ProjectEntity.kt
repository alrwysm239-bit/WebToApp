package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val htmlContent: String,
    val assetsJson: String,
    val appConfigJson: String,
    val publishedUrl: String?,
    val lastProviderKey: String?,
    val isFavorite: Boolean = false,
    val updatedAt: Long,
)

@Entity(tableName = "project_history")
data class ProjectHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val label: String,
    val htmlContent: String,
    val assetsJson: String,
    val createdAt: Long,
)
