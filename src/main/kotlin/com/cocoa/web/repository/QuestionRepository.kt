package com.cocoa.web.repository

import com.cocoa.generated.form.Tables.QUESTION
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.model.Question
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class QuestionRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    fun batchUpdate(questions: List<Question.Request.Edit>) {
        dsl.batched { ctx ->
            questions.forEach { question ->
                ctx.dsl().update(QUESTION)
                    .set(QUESTION.IS_ACTIVE, question.isActive)
                    .set(QUESTION.IS_MANDATORY, question.isMandatory)
                    .where(QUESTION.QUESTION_ID.eq(question.questionId))
                    .execute()
            }
        }
    }
}
