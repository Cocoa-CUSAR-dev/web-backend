package com.cocoa.web.repository

import com.cocoa.generated.form.Tables.QUESTION
import com.cocoa.generated.form.Tables.SECTION
import com.cocoa.generated.form.Tables.TASK_FORM
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.model.Form
import com.cocoa.web.model.Question
import com.cocoa.web.model.Section
import com.cocoa.web.util.JsonUtils
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
class FormRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    fun findByFormId(formId: UUID): Form.Entity? {
        val formRecord =
            dsl.selectFrom(TASK_FORM)
                .where(TASK_FORM.FORM_ID.eq(formId))
                .fetchOne()

        return formRecord?.toFormEntity()
    }

    fun findByTaskId(taskId: UUID): Form.Entity? {
        val formRecord =
            dsl.selectFrom(TASK_FORM)
                .where(TASK_FORM.TASK_ID.eq(taskId))
                .fetchOne()

        return formRecord?.toFormEntity()
    }

    fun fetchForms(): List<Form.Entity> {
        val formRecords =
            dsl.selectDistinct(
                TASK_FORM.FORM_ID,
                TASK_FORM.TASK_ID,
                TASK_FORM.TITLE,
                TASK_FORM.CONTENT,
                TASK_FORM.HANDLER,
                TASK_FORM.IS_MULTIPLE_SUBMIT,
                TASK_FORM.DESCRIPTION,
                TASK_FORM.CREATED_AT,
            )
                .from(TASK_FORM)
                .whereExists(
                    dsl.selectOne()
                        .from(SECTION)
                        .where(SECTION.FORM_ID.eq(TASK_FORM.FORM_ID)),
                )
                .fetch()

        return formRecords.map { it.toFormEntity() }
    }

    fun fetchForm(formId: UUID): Form.Detail? {
        val records = queryFormRecords(formId).ifEmpty { return null }
        val first = records.first()

        return Form.Detail(
            formId = first.get(TASK_FORM.FORM_ID),
            title = first.get(TASK_FORM.TITLE),
            description = first.get(TASK_FORM.DESCRIPTION),
            sections = buildSections(records),
        )
    }

    // Helper Functions
    private fun queryFormRecords(formId: UUID): List<Record> {
        return dsl.select(
            TASK_FORM.FORM_ID,
            TASK_FORM.TITLE,
            TASK_FORM.DESCRIPTION,
            SECTION.SECTION_ID,
            SECTION.TITLE,
            SECTION.DESCRIPTION,
            SECTION.SORT_ORDER,
            SECTION.IS_ACTIVE,
            QUESTION.QUESTION_ID,
            QUESTION.SECTION_ID,
            QUESTION.LABEL,
            QUESTION.INPUT_TYPE,
            QUESTION.DESCRIPTION,
            QUESTION.FIELD_NAME,
            QUESTION.DEFAULT_VALUE,
            QUESTION.IS_MANDATORY,
            QUESTION.IS_ACTIVE,
            QUESTION.SORT_ORDER,
        )
            .from(TASK_FORM)
            .leftJoin(SECTION).on(SECTION.FORM_ID.eq(TASK_FORM.FORM_ID))
            .leftJoin(QUESTION).on(QUESTION.SECTION_ID.eq(SECTION.SECTION_ID))
            .where(TASK_FORM.FORM_ID.eq(formId))
            .orderBy(SECTION.SORT_ORDER, QUESTION.SORT_ORDER)
            .fetch()
    }

    private fun buildSections(records: List<Record>): List<Section.Detail> {
        val optionFieldNames =
            records
                .filter { it.get(QUESTION.INPUT_TYPE) == "OPTION" && it.get(QUESTION.FIELD_NAME) != null }
                .map { it.get(QUESTION.FIELD_NAME) }
                .distinct()

        val choicesMap =
            optionFieldNames.associateWith { fieldName ->
                fetchRefChoices(fieldName)
            }

        return records
            .groupBy { it.get(SECTION.SECTION_ID) }
            .filterKeys { it != null }
            .map { (_, sectionRecords) ->
                val first = sectionRecords.first()
                Section.Detail(
                    sectionId = first.get(SECTION.SECTION_ID),
                    title = first.get(SECTION.TITLE),
                    description = first.get(SECTION.DESCRIPTION),
                    sortOrder = first.get(SECTION.SORT_ORDER),
                    isActive = first.get(SECTION.IS_ACTIVE),
                    questions =
                        sectionRecords
                            .filter { it.get(QUESTION.QUESTION_ID) != null }
                            .map { it.toQuestionEntity(choicesMap) },
                )
            }
    }

    // dsl.meta().tables walks the entire live schema — expensive, and was
    // being paid once per option field, on every single form request. Which
    // table/column a field name resolves to only changes via a migration +
    // restart, so that resolution is safe to cache in-process; the actual
    // choice rows below are still queried fresh every time.
    private data class RefChoiceFields(val table: Table<*>, val idField: Field<*>, val nameField: Field<*>)

    private val refChoiceFieldCache = ConcurrentHashMap<String, RefChoiceFields>()

    private fun fetchRefChoices(fieldName: String): List<Question.Choice> {
        val (table, idField, nameField) =
            refChoiceFieldCache.getOrPut(fieldName) {
                val tableName = fieldName.removeSuffix("_id") + "_constant"
                val namePrefix = fieldName.removeSuffix("_id") + "_name"

                val table =
                    dsl.meta().tables.find { it.name == tableName }
                        ?: throw IllegalArgumentException("Unknown ref table: $tableName")

                // fieldName (e.g. plot_id) is also the ref table's own PK column
                // name by convention, verified against every *_constant table.
                val idField =
                    table.fields().firstOrNull { it.name == fieldName }
                        ?: throw IllegalArgumentException("No id field named '$fieldName' in $tableName")

                val nameField =
                    table.fields().firstOrNull { it.name.startsWith(namePrefix) }
                        ?: throw IllegalArgumentException("No name field starting with '$namePrefix' in $tableName")

                RefChoiceFields(table, idField, nameField)
            }

        return dsl.select(idField, nameField)
            .from(table)
            .fetch { record -> Question.Choice(id = record.get(idField).toString(), name = record.get(nameField) as String) }
    }

    private fun Record.toQuestionEntity(choicesMap: Map<String, List<Question.Choice>>): Question.Entity {
        val fieldName = this.get(QUESTION.FIELD_NAME)
        val inputType = this.get(QUESTION.INPUT_TYPE)

        return Question.Entity(
            questionId = this.get(QUESTION.QUESTION_ID),
            sectionId = this.get(QUESTION.SECTION_ID),
            label = this.get(QUESTION.LABEL),
            inputType = inputType,
            description = this.get(QUESTION.DESCRIPTION),
            fieldName = fieldName,
            defaultValue = this.get(QUESTION.DEFAULT_VALUE)?.let(JsonUtils::read),
            isMandatory = this.get(QUESTION.IS_MANDATORY),
            isActive = this.get(QUESTION.IS_ACTIVE),
            sortOrder = this.get(QUESTION.SORT_ORDER),
            choices =
                if (inputType == "OPTION") {
                    fieldName?.let { choicesMap[it] } ?: emptyList()
                } else {
                    null
                },
        )
    }

    private fun Record.toFormEntity(): Form.Entity {
        return Form.Entity(
            formId = this.get(TASK_FORM.FORM_ID),
            taskId = this.get(TASK_FORM.TASK_ID),
            title = this.get(TASK_FORM.TITLE),
            content = this.get(TASK_FORM.CONTENT),
            handler = this.get(TASK_FORM.HANDLER),
            isMultipleSubmit = this.get(TASK_FORM.IS_MULTIPLE_SUBMIT),
            description = this.get(TASK_FORM.DESCRIPTION),
            createdAt = this.get(TASK_FORM.CREATED_AT),
        )
    }

//    private fun Record.toQuestionEntity(): Question.Entity {
//        return Question.Entity(
//            questionId = this.get(QUESTION.QUESTION_ID),
//            sectionId = this.get(QUESTION.SECTION_ID),
//            label = this.get(QUESTION.LABEL),
//            inputType = this.get(QUESTION.INPUT_TYPE),
//            description = this.get(QUESTION.DESCRIPTION),
//            fieldName = this.get(QUESTION.FIELD_NAME),
//            defaultValue = this.get(QUESTION.DEFAULT_VALUE),
//            isMandatory = this.get(QUESTION.IS_MANDATORY),
//            isActive = this.get(QUESTION.IS_ACTIVE),
//            sortOrder = this.get(QUESTION.SORT_ORDER),
//        )
//    }
}
