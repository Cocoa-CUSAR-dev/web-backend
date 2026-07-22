package com.cocoa.web.repository

import com.cocoa.generated.form.Tables.TASK
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.Task
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class TaskRepository(
    dsl: DSLContext,
): BaseRepository(dsl) {

    fun fetchTasks(): List<Task.Entity> {
        val records = dsl.selectFrom(TASK).fetch()

        return records.map { it.toTaskEntity() }
    }

    fun fetchTask(taskId: UUID): Task.Entity? {
        val record = dsl.selectFrom(TASK)
            .where(TASK.TASK_ID.eq(taskId))
            .fetchOne()

        return record?.toTaskEntity()
    }

    // Helper Functions
    private fun Record.toTaskEntity(): Task.Entity {
        return Task.Entity(
            taskId = this.get(TASK.TASK_ID),
            title = this.get(TASK.TITLE),
            description = this.get(TASK.DESCRIPTION),
            taskType = this.get(TASK.TASK_TYPE),
            openAt = this.get(TASK.OPEN_AT),
            closeAt = this.get(TASK.CLOSE_AT),
        )
    }

}