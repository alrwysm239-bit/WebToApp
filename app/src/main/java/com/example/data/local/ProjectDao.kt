package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun getAllProjectsOnce(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity): Long

    @Query("UPDATE projects SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()

    @Query("SELECT * FROM project_history WHERE projectId = :projectId ORDER BY createdAt DESC LIMIT 25")
    fun observeHistoryForProject(projectId: Long): Flow<List<ProjectHistoryEntity>>

    @Query("SELECT * FROM project_history WHERE id = :snapshotId LIMIT 1")
    suspend fun getSnapshotById(snapshotId: Long): ProjectHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(snapshot: ProjectHistoryEntity): Long

    @Query("DELETE FROM project_history WHERE projectId = :projectId")
    suspend fun deleteHistoryForProject(projectId: Long)

    @Query("DELETE FROM project_history")
    suspend fun deleteAllHistory()
}
