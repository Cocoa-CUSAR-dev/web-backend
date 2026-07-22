package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.Task
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.TaskService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/tasks")
class TaskController(
    private val taskService: TaskService,
): BaseController() {

    @GetMapping
    fun getTasks(): ResponseEntity<ApiResponse<List<Task.Entity>>> {
        val tasks = taskService.getTasks()

        return tasks.toResponseEntity(HttpStatus.OK)
    }

    @GetMapping("/{taskId}")
    fun getTask(
        @PathVariable taskId: UUID,
    ): ResponseEntity<ApiResponse<Task.Entity>> {
        val task = taskService.getTask(taskId)

        return task.toResponseEntity((HttpStatus.OK))
    }

}