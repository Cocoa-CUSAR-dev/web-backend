package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.Task
import com.cocoa.web.repository.TaskRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TaskService(
    private val taskRepository: TaskRepository,
) : BaseService() {
    fun getTasks(): List<Task.Entity> {
        return taskRepository.fetchTasks()
    }

    fun getTask(taskId: UUID): Task.Entity {
        return taskRepository.fetchTask(taskId)
            ?: throw EntityNotFoundException("Task Not Found")
    }
}
