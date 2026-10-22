package com.cocoa.web.repository

import com.cocoa.web.base.PageRequest
import org.jooq.CloseableDSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * docs-and-plan#231: the SQL behind the farmer's history page, against a REAL
 * Postgres -- which days a farmer has submitted on (paged, newest first, in
 * the farmer's own Asia/Bangkok day) and the answers of one such day.
 *
 * Runs in CI against the job's own throwaway database (ci-schema.sql), which
 * sets COCOA_CI_SCHEMA_JDBC_URL. Deliberately a separate variable from
 * SPRING_DATASOURCE_URL: a shell pointed at a real database must never run
 * this by accident. Locally:
 *
 *   COCOA_CI_SCHEMA_JDBC_URL=jdbc:postgresql://localhost:5432/cocoa_test \
 *   COCOA_CI_SCHEMA_USER=postgres COCOA_CI_SCHEMA_PASSWORD=postgres \
 *   ./gradlew test --tests "*HistoryRepositoryDbTest"
 *
 * Everything it writes belongs to users and forms it created, and is removed
 * after each test.
 */
@EnabledIfEnvironmentVariable(named = "COCOA_CI_SCHEMA_JDBC_URL", matches = ".+")
class HistoryRepositoryDbTest {
    private val url = System.getenv("COCOA_CI_SCHEMA_JDBC_URL")
    private val user = System.getenv("COCOA_CI_SCHEMA_USER") ?: ""
    private val password = System.getenv("COCOA_CI_SCHEMA_PASSWORD") ?: ""

    private lateinit var dsl: CloseableDSLContext
    private lateinit var repository: FormResponseRepository

    private val farmer = UUID.randomUUID()
    private val otherFarmer = UUID.randomUUID()
    private val formIds = mutableListOf<UUID>()

    @BeforeEach
    fun connect() {
        dsl = DSL.using(url, user, password)
        repository = FormResponseRepository(dsl)
    }

    @AfterEach
    fun cleanUp() {
        dsl.execute("delete from form.response where user_id in (?, ?)", farmer, otherFarmer)
        formIds.forEach { formId ->
            dsl.execute(
                "delete from form.question where section_id in (select section_id from form.section where form_id = ?)",
                formId,
            )
            dsl.execute("delete from form.section where form_id = ?", formId)
        }
        dsl.close()
    }

    // ---- fixtures --------------------------------------------------------

    /** submitted_at is UTC wall-clock, as every backend writes it. */
    private fun submit(
        userId: UUID,
        submittedAtUtc: LocalDateTime,
        formId: UUID = UUID.randomUUID(),
        answer: String = "{}",
    ) {
        dsl.execute(
            "insert into form.response (response_id, task_log_id, task_form_id, user_id, submitted_at, answer) " +
                "values (?, ?, ?, ?, ?, cast(? as jsonb))",
            UUID.randomUUID(),
            UUID.randomUUID(),
            formId,
            userId,
            submittedAtUtc,
            answer,
        )
    }

    private fun formWithQuestions(vararg questions: Triple<String, String?, String>): UUID {
        val formId = UUID.randomUUID().also { formIds += it }
        val sectionId = UUID.randomUUID()
        dsl.execute("insert into form.section (section_id, form_id) values (?, ?)", sectionId, formId)
        questions.forEach { (fieldName, label, inputType) ->
            dsl.execute(
                "insert into form.question (question_id, section_id, field_name, label, input_type) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                sectionId,
                fieldName,
                label,
                inputType,
            )
        }
        return formId
    }

    private fun utc(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ) = LocalDateTime.of(2026, 9, day, hour, minute)

    private fun sept(day: Int) = LocalDate.of(2026, 9, day)

    // ---- submission days -------------------------------------------------

    @Test
    fun `days are the farmer's own Bangkok calendar days, newest first, each once`() {
        // Same UTC day, two Bangkok days: only bucketing in Asia/Bangkok tells
        // them apart (UTC would report a single Sep 17).
        submit(farmer, utc(17, 16, 59)) // 23:59 Bangkok, Sep 17
        submit(farmer, utc(17, 17, 17)) // 00:17 Bangkok, already Sep 18
        submit(farmer, utc(17, 20)) // 03:00 Bangkok, Sep 18 again
        submit(farmer, utc(20, 5)) // Sep 20

        assertEquals(listOf(sept(20), sept(18), sept(17)), repository.fetchOwnSubmissionDays(farmer, PageRequest()))
    }

    @Test
    fun `pages count days, not submissions, and never repeat or skip one`() {
        // Five days, the busiest with three submissions -- paging by rows would
        // split or repeat it.
        listOf(utc(16, 3), utc(17, 3), utc(18, 1), utc(18, 3), utc(18, 9), utc(19, 3), utc(20, 3))
            .forEach { submit(farmer, it) }

        val pages = (0..3).map { repository.fetchOwnSubmissionDays(farmer, PageRequest(page = it, size = 2)) }

        assertEquals(listOf(sept(20), sept(19)), pages[0])
        assertEquals(listOf(sept(18), sept(17)), pages[1])
        assertEquals(listOf(sept(16)), pages[2])
        assertEquals(emptyList<LocalDate>(), pages[3])
    }

    @Test
    fun `a farmer only ever sees their own days`() {
        submit(farmer, utc(18, 3))
        submit(otherFarmer, utc(19, 3))

        assertEquals(listOf(sept(18)), repository.fetchOwnSubmissionDays(farmer, PageRequest()))
        assertEquals(listOf(sept(19)), repository.fetchOwnSubmissionDays(otherFarmer, PageRequest()))
    }

    @Test
    fun `a farmer with no submissions has no days`() {
        assertEquals(emptyList<LocalDate>(), repository.fetchOwnSubmissionDays(farmer, PageRequest()))
    }

    // ---- one day's answers -----------------------------------------------

    @Test
    fun `a day's answers come from that Bangkok day only, labelled by the form's own questions`() {
        val formId =
            formWithQuestions(
                Triple("harvest_weight", "น้ำหนักผลผลิต", "FLOAT"),
                Triple("notes", null, "VARCHAR"),
            )
        // 00:17 Bangkok on Sep 18 -- UTC still says Sep 17.
        submit(farmer, utc(17, 17, 17), formId, """{"harvest_weight": "12.5", "notes": "ฝนตก"}""")
        // 23:59 Bangkok on Sep 17: a different day.
        submit(farmer, utc(17, 16, 59), formId, """{"harvest_weight": "99"}""")
        // Same day, someone else.
        submit(otherFarmer, utc(18, 3), formId, """{"harvest_weight": "7"}""")

        val answers = repository.fetchAnswerFieldsForUserAndDate(farmer, sept(18))

        assertEquals(
            listOf(
                Triple("harvest_weight", "น้ำหนักผลผลิต", "12.5"),
                // No label on the question: the field name stands in.
                Triple("notes", "notes", "ฝนตก"),
            ),
            answers.map { Triple(it.fieldName, it.label, it.rawValue) }.sortedBy { it.first },
        )
    }

    @Test
    fun `empty, blank and unknown answers are left out`() {
        val formId = formWithQuestions(Triple("harvest_weight", "น้ำหนักผลผลิต", "FLOAT"), Triple("notes", "หมายเหตุ", "VARCHAR"))
        submit(
            farmer,
            utc(18, 3),
            formId,
            """{"harvest_weight": null, "notes": "   ", "not_on_this_form": "x"}""",
        )

        assertEquals(emptyList<Any>(), repository.fetchAnswerFieldsForUserAndDate(farmer, sept(18)))
    }

    @Test
    fun `a day with nothing submitted has no answers`() {
        submit(farmer, utc(18, 3), formWithQuestions(Triple("notes", "หมายเหตุ", "VARCHAR")), """{"notes": "ok"}""")

        assertEquals(emptyList<Any>(), repository.fetchAnswerFieldsForUserAndDate(farmer, sept(20)))
    }
}
