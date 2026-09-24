package com.example.data.db

import com.example.data.model.StackedProject
import kotlinx.coroutines.flow.Flow

class StackedProjectRepository(private val dao: StackedProjectDao) {
    val allProjects: Flow<List<StackedProject>> = dao.getAllProjects()

    suspend fun getProjectById(id: Long): StackedProject? = dao.getProjectById(id)

    suspend fun saveProject(project: StackedProject): Long = dao.insertProject(project)

    suspend fun updateProject(project: StackedProject) = dao.updateProject(project)

    suspend fun deleteProject(project: StackedProject) = dao.deleteProject(project)

    suspend fun deleteProjectById(id: Long) = dao.deleteProjectById(id)
}
