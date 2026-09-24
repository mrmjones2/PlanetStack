package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.StackedProject
import kotlinx.coroutines.flow.Flow

@Dao
interface StackedProjectDao {
    @Query("SELECT * FROM stacked_projects ORDER BY timestamp DESC")
    fun getAllProjects(): Flow<List<StackedProject>>

    @Query("SELECT * FROM stacked_projects WHERE id = :id")
    suspend fun getProjectById(id: Long): StackedProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: StackedProject): Long

    @Update
    suspend fun updateProject(project: StackedProject)

    @Delete
    suspend fun deleteProject(project: StackedProject)

    @Query("DELETE FROM stacked_projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)
}
