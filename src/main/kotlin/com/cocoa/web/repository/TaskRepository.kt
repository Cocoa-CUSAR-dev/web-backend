package com.cocoa.web.repository

import com.cocoa.generated.form.Tables.TASK
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.base.PageRequest
import com.cocoa.web.model.Task
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class TaskRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    // BE-9: was an unbounded .fetch() with no order -- added a stable sort
    // (open_at desc, then task_id as a tiebreaker so equal open_at values
    // don't reshuffle between pages) since limit/offset without one can
    // return a row twice or skip one across pages.
    fun fetchTasks(pageRequest: PageRequest = PageRequest()): List<Task.Entity> {
        val records =
            dsl.selectFrom(TASK)
                .orderBy(TASK.OPEN_AT.desc(), TASK.TASK_ID)
                .limit(pageRequest.size)
                .offset(pageRequest.offset)
                .fetch()

        return records.map { it.toTaskEntity() }
    }

    fun fetchTask(taskId: UUID): Task.Entity? {
        val record =
            dsl.selectFrom(TASK)
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
