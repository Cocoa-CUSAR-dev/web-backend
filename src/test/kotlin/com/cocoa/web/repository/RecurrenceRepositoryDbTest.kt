package com.cocoa.web.repository

import com.cocoa.web.model.Form
import com.cocoa.web.model.Question
import com.cocoa.web.model.Recurrence
import com.cocoa.web.model.Reminder
import com.cocoa.web.model.Section
import org.jooq.CloseableDSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs the recurring-task SQL (form creation with a recurrence, the round
 * copy, recipient resolution) against a REAL Postgres.
 *
 * Skipped unless COCOA_FULL_SCHEMA_JDBC_URL is set, because CI's database only
 * has ci-schema.sql's few tables, not the real constraints, defaults and the
 * rest of the schema this depends on. To run it, build a database from the
 * full migration chain (the `database` repo, V1..V31) and:
 *
 *   COCOA_FULL_SCHEMA_JDBC_URL=jdbc:postgresql://localhost:5432/cocoa_test \
 *   COCOA_FULL_SCHEMA_USER=... COCOA_FULL_SCHEMA_PASSWORD=... \
 *   ./gradlew test --tests "*RecurrenceRepositoryDbTest"
 *
 * Everything it creates is titled with the IT_MARK prefix (and its users and
 * roles named likewise) and is deleted after each test. Do NOT point it at a
 * database whose data matters.
 */
@EnabledIfEnvironmentVariable(named = "COCOA_FULL_SCHEMA_JDBC_URL", matches = ".+")
class RecurrenceRepositoryDbTest {
    private companion object {
        const val IT_MARK = "IT-RECUR-"
    }

    private val url = System.getenv("COCOA_FULL_SCHEMA_JDBC_URL")
    private val user = System.getenv("COCOA_FULL_SCHEMA_USER") ?: ""
    private val password = System.getenv("COCOA_FULL_SCHEMA_PASSWORD") ?: ""

    private lateinit var dsl: CloseableDSLContext
    private lateinit var forms: FormRepository
    private lateinit var recurrences: RecurrenceRepository

    private val opens = LocalDateTime.of(2026, 10, 5, 8, 0)
    private val closes = LocalDateTime.of(2026, 10, 5, 17, 0)

    @BeforeEach
    fun connect() {
        dsl = DSL.using(url, user, password)
        forms = FormRepository(dsl)
        recurrences = RecurrenceRepository(dsl)
        cleanup()
    }

    @AfterEach
    fun disconnect() {
        cleanup()
        dsl.close()
    }

    // ---- fixtures --------------------------------------------------------

    private fun newUser(name: String): UUID {
        val id = UUID.randomUUID()
        dsl.execute("insert into auth.user_account (user_id, username) values (?, ?)", id, "$IT_MARK$name")
        return id
    }

    private fun newRole(name: String): UUID {
        val id = UUID.randomUUID()
        dsl.execute("insert into auth.role (role_id, role_name) values (?, ?)", id, "$IT_MARK$name")
        return id
    }

    private fun giveRole(
        user: UUID,
        role: UUID,
    ) {
        dsl.execute("insert into auth.user_role (user_id, role_id) values (?, ?)", user, role)
    }

    private fun cleanup() {
        val mine = "select task_id from form.task where title like '$IT_MARK%'"
        val myQuestions =
            """select q.question_id from form.question q
               join form.section s on s.section_id = q.section_id
               join form.task_form f on f.form_id = s.form_id
               where f.task_id in ($mine)"""
        dsl.execute("update form.task set recurrence_id = null, period_date = null where title like '$IT_MARK%'")
        dsl.execute("delete from form.assignment where task_id in ($mine)")
        dsl.execute("delete from form.task_recipient where task_id in ($mine)")
        dsl.execute("delete from form.question_visibility where question_id in ($myQuestions)")
        dsl.execute("delete from form.question where question_id in ($myQuestions)")
        dsl.execute(
            "delete from form.section where form_id in (select form_id from form.task_form where task_id in ($mine))",
        )
        dsl.execute("delete from form.task_form where task_id in ($mine)")
        dsl.execute("delete from form.task_recurrence where template_task_id in ($mine)")
        dsl.execute("delete from form.task where title like '$IT_MARK%'")
        dsl.execute("delete from auth.user_role where user_id in (select user_id from auth.user_account where username like '$IT_MARK%')")
        dsl.execute("delete from auth.user_role where role_id in (select role_id from auth.role where role_name like '$IT_MARK%')")
        dsl.execute("delete from auth.user_account where username like '$IT_MARK%'")
        dsl.execute("delete from auth.role where role_name like '$IT_MARK%'")
    }

    private fun createRequest(
        recurrence: Recurrence.Request.Create? = null,
        recipients: List<Reminder.Recipient> = emptyList(),
        title: String = "${IT_MARK}daily check",
    ) = Form.Request.Create(
        title = title,
        description = "check the pods",
        openAt = opens,
        closeAt = closes,
        handler = "farm_activity",
        isMultipleSubmit = true,
        sections =
            listOf(
                Section.Request.Create(
                    "Pods",
                    "first",
                    1,
                    listOf(
                        Question.Request.Create("Count", "how many", "INT", "pod_count", isMandatory = true, sortOrder = 1),
                        Question.Request.Create("Note", null, "VARCHAR", "note", carryForward = true, sortOrder = 2),
                    ),
                ),
                Section.Request.Create(
                    "Weather",
                    null,
                    2,
                    listOf(Question.Request.Create("Rain", null, "BOOLEAN", "rain", sortOrder = 1)),
                ),
            ),
        recurrence = recurrence,
        recipients = recipients,
    )

    private fun taskIdOf(formId: UUID): UUID = forms.findByFormId(formId)!!.taskId

    private fun count(sql: String): Int = dsl.fetchValue(sql)?.toString()?.toInt() ?: 0

    // ---- creating a repeating task ----------------------------------------

    @Test
    fun `creating a weekly task records the recurrence, points the task at it for its first day`() {
        val creator = newUser("creator")
        val formId =
            forms.createForm(
                createRequest(Recurrence.Request.Create(Recurrence.Frequency.WEEKLY, listOf(4, 1, 4), LocalDate.of(2026, 12, 31))),
                creator,
            )
        val taskId = taskIdOf(formId)

        val rec = dsl.fetchOne("select * from form.task_recurrence where template_task_id = ?", taskId)!!
        assertEquals("WEEKLY", rec.get("frequency"))
        assertEquals(listOf<Short>(1, 4), (rec.get("weekdays") as Array<*>).map { it as Short }) // sorted, no duplicates
        assertEquals(LocalDate.of(2026, 10, 5), (rec.get("start_date") as java.sql.Date).toLocalDate())
        assertEquals(LocalDate.of(2026, 12, 31), (rec.get("until_date") as java.sql.Date).toLocalDate())
        assertEquals(true, rec.get("is_active"))
        assertEquals(creator, rec.get("created_by"))

        val task = dsl.fetchOne("select recurrence_id, period_date from form.task where task_id = ?", taskId)!!
        assertEquals(rec.get("recurrence_id"), task.get("recurrence_id"))
        assertEquals(LocalDate.of(2026, 10, 5), (task.get("period_date") as java.sql.Date).toLocalDate())
    }

    @Test
    fun `a daily task stores no weekday list`() {
        val formId = forms.createForm(createRequest(Recurrence.Request.Create(Recurrence.Frequency.DAILY)), newUser("c"))

        val weekdays = dsl.fetchValue("select weekdays from form.task_recurrence where template_task_id = ?", taskIdOf(formId))
        assertNull(weekdays)
    }

    @Test
    fun `a one-off task creates no recurrence and no assignment`() {
        val formId = forms.createForm(createRequest(), newUser("c"))
        val taskId = taskIdOf(formId)

        assertEquals(0, count("select count(*) from form.task_recurrence where template_task_id = '$taskId'"))
        assertEquals(0, count("select count(*) from form.assignment where task_id = '$taskId'"))
        assertEquals(0, count("select count(*) from form.task_recipient where task_id = '$taskId'"))
        assertNull(dsl.fetchValue("select recurrence_id from form.task where task_id = ?", taskId))
    }

    @Test
    fun `recipients are stored as rules and the people they name are assigned`() {
        val creator = newUser("creator")
        val farmers = newRole("farmer")
        val a = newUser("a")
        val b = newUser("b")
        val outsider = newUser("outsider")
        giveRole(a, farmers)
        giveRole(b, farmers)
        val rules =
            listOf(
                Reminder.Recipient(Reminder.RecipientType.ROLE, farmers),
                Reminder.Recipient(Reminder.RecipientType.USER, a), // also named directly: still one assignment
            )

        val assignees = recurrences.resolveUserIds(rules)
        val formId = forms.createForm(createRequest(recipients = rules), creator, assignees)
        val taskId = taskIdOf(formId)

        assertEquals(setOf(a, b), assignees.toSet())
        assertEquals(2, count("select count(*) from form.task_recipient where task_id = '$taskId'"))
        val assigned = dsl.fetch("select user_id from form.assignment where task_id = ?", taskId).map { it.get("user_id") as UUID }
        assertEquals(setOf(a, b), assigned.toSet())
        assertTrue(outsider !in assigned)
        assertEquals(
            rules.toSet(),
            recurrences.fetchRecipients(taskId).toSet(),
        )
    }

    // ---- recipients ---------------------------------------------------------

    @Test
    fun `resolveUserIds ignores a user rule whose account does not exist`() {
        val real = newUser("real")

        val ids =
            recurrences.resolveUserIds(
                listOf(
                    Reminder.Recipient(Reminder.RecipientType.USER, real),
                    Reminder.Recipient(Reminder.RecipientType.USER, UUID.randomUUID()),
                ),
            )

        assertEquals(listOf(real), ids)
    }

    @Test
    fun `findUnknownRecipients names rules whose role or user does not exist`() {
        val role = newRole("known-role")
        val user = newUser("known-user")
        val ghostRole = Reminder.Recipient(Reminder.RecipientType.ROLE, UUID.randomUUID())
        val ghostUser = Reminder.Recipient(Reminder.RecipientType.USER, UUID.randomUUID())

        val unknown =
            recurrences.findUnknownRecipients(
                listOf(
                    Reminder.Recipient(Reminder.RecipientType.ROLE, role),
                    Reminder.Recipient(Reminder.RecipientType.USER, user),
                    ghostRole,
                    ghostUser,
                ),
            )

        assertEquals(setOf(ghostRole, ghostUser), unknown.toSet())
    }

    // ---- which recurrences the job looks at ---------------------------------

    @Test
    fun `fetchRunning returns an active recurrence with its template's times, and skips inactive or long-ended ones`() {
        val creator = newUser("c")
        val running =
            taskIdOf(
                forms.createForm(
                    createRequest(Recurrence.Request.Create(Recurrence.Frequency.WEEKLY, listOf(1, 4)), title = "${IT_MARK}running"),
                    creator,
                ),
            )
        val switchedOff =
            taskIdOf(
                forms.createForm(createRequest(Recurrence.Request.Create(Recurrence.Frequency.DAILY), title = "${IT_MARK}off"), creator),
            )
        val ended =
            taskIdOf(
                forms.createForm(
                    createRequest(
                        Recurrence.Request.Create(Recurrence.Frequency.DAILY, untilDate = LocalDate.of(2026, 10, 6)),
                        title = "${IT_MARK}ended",
                    ),
                    creator,
                ),
            )
        dsl.execute("update form.task_recurrence set is_active = false where template_task_id = ?", switchedOff)

        val found =
            recurrences.fetchRunning(windowStart = LocalDate.of(2026, 10, 10)).filter {
                it.templateTaskId in setOf(running, switchedOff, ended)
            }

        assertEquals(listOf(running), found.map { it.templateTaskId })
        val r = found.single()
        assertEquals(Recurrence.Frequency.WEEKLY, r.frequency)
        assertEquals(listOf(1, 4), r.weekdays)
        assertEquals(LocalDate.of(2026, 10, 5), r.startDate)
        assertEquals(opens, r.templateOpenAt)
        assertEquals(closes, r.templateCloseAt)
    }

    // ---- opening a round ----------------------------------------------------

    private fun templateWithVisibility(): Pair<Recurrence.Active, UUID> {
        val creator = newUser("creator")
        val farmers = newRole("farmer")
        val a = newUser("a")
        giveRole(a, farmers)
        val rules = listOf(Reminder.Recipient(Reminder.RecipientType.ROLE, farmers))
        val formId =
            forms.createForm(
                createRequest(Recurrence.Request.Create(Recurrence.Frequency.DAILY), rules),
                creator,
                recurrences.resolveUserIds(rules),
            )
        val templateTask = taskIdOf(formId)
        // Who may see the "Count" question: farmers only.
        dsl.execute(
            "insert into form.question_visibility (question_id, role) " +
                "select question_id, 'farmer' from form.question where field_name = 'pod_count' and section_id in " +
                "(select section_id from form.section where form_id = ?)",
            formId,
        )
        val active = recurrences.fetchRunning(LocalDate.of(2026, 1, 1)).single { it.templateTaskId == templateTask }
        return active to a
    }

    @Test
    fun `createRound copies the task, form, sections, questions and visibility, and assigns the people`() {
        val (recurrence, person) = templateWithVisibility()

        val newTask =
            recurrences.createRound(
                recurrence,
                LocalDate.of(2026, 10, 6),
                LocalDateTime.of(2026, 10, 6, 8, 0),
                LocalDateTime.of(2026, 10, 6, 17, 0),
                listOf(person),
            )

        assertNotNull(newTask)
        val task = dsl.fetchOne("select * from form.task where task_id = ?", newTask)!!
        assertEquals("${IT_MARK}daily check", task.get("title"))
        assertEquals("check the pods", task.get("description"))
        assertEquals(recurrence.recurrenceId, task.get("recurrence_id"))
        assertEquals(LocalDate.of(2026, 10, 6), (task.get("period_date") as java.sql.Date).toLocalDate())
        assertEquals(LocalDateTime.of(2026, 10, 6, 8, 0), (task.get("open_at") as java.sql.Timestamp).toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 10, 6, 17, 0), (task.get("close_at") as java.sql.Timestamp).toLocalDateTime())

        val newForm = forms.findByTaskId(newTask!!)!!
        val oldForm = forms.findByTaskId(recurrence.templateTaskId)!!
        assertNotEquals(oldForm.formId, newForm.formId)
        assertEquals("farm_activity", newForm.handler)
        assertEquals(true, newForm.isMultipleSubmit)

        // Same structure, brand-new rows.
        fun shape(formId: UUID) =
            dsl.fetch(
                "select s.title as st, s.sort_order as so, q.label, q.input_type, q.field_name, q.is_mandatory, q.carry_forward, q.sort_order " +
                    "from form.section s join form.question q on q.section_id = s.section_id " +
                    "where s.form_id = ? order by s.sort_order, q.sort_order",
                formId,
            ).map { it.intoArray().toList() }
        assertEquals(shape(oldForm.formId), shape(newForm.formId))
        assertEquals(3, shape(newForm.formId).size)
        assertEquals(
            0,
            count(
                "select count(*) from form.question where section_id in (select section_id from form.section where form_id = '${newForm.formId}') " +
                    "and question_id in (select question_id from form.question where section_id in " +
                    "(select section_id from form.section where form_id = '${oldForm.formId}'))",
            ),
        )

        // The visibility rule moved to the copied question.
        assertEquals(
            1,
            count(
                "select count(*) from form.question_visibility v join form.question q on q.question_id = v.question_id " +
                    "join form.section s on s.section_id = q.section_id where s.form_id = '${newForm.formId}' and q.field_name = 'pod_count' and v.role = 'farmer'",
            ),
        )

        assertEquals(
            listOf(person),
            dsl.fetch("select user_id from form.assignment where task_id = ?", newTask).map { it.get("user_id") as UUID },
        )
    }

    @Test
    fun `createRound for a day that already has its round creates nothing and returns null`() {
        val (recurrence, person) = templateWithVisibility()
        val day = LocalDate.of(2026, 10, 6)
        val first = recurrences.createRound(recurrence, day, day.atTime(8, 0), day.atTime(17, 0), listOf(person))
        val tasksBefore = count("select count(*) from form.task where title like '$IT_MARK%'")
        val formsBefore =
            count("select count(*) from form.task_form tf join form.task t on t.task_id = tf.task_id where t.title like '$IT_MARK%'")

        val second = recurrences.createRound(recurrence, day, day.atTime(8, 0), day.atTime(17, 0), listOf(person))

        assertNotNull(first)
        assertNull(second)
        assertEquals(tasksBefore, count("select count(*) from form.task where title like '$IT_MARK%'"))
        assertEquals(
            formsBefore,
            count("select count(*) from form.task_form tf join form.task t on t.task_id = tf.task_id where t.title like '$IT_MARK%'"),
        )
    }

    @Test
    fun `the template's own first day counts as already having its round`() {
        val (recurrence, person) = templateWithVisibility()

        val again = recurrences.createRound(recurrence, recurrence.startDate, opens, closes, listOf(person))

        assertNull(again)
    }

    @Test
    fun `fetchExistingPeriods lists only the days that already have a round`() {
        val (recurrence, person) = templateWithVisibility()
        val d6 = LocalDate.of(2026, 10, 6)
        recurrences.createRound(recurrence, d6, d6.atTime(8, 0), d6.atTime(17, 0), listOf(person))

        val existing =
            recurrences.fetchExistingPeriods(
                recurrence.recurrenceId,
                listOf(LocalDate.of(2026, 10, 5), d6, LocalDate.of(2026, 10, 7)),
            )

        assertEquals(setOf(LocalDate.of(2026, 10, 5), d6), existing)
    }

    @Test
    fun `two runs opening the same day at the same moment create exactly one round`() {
        val (recurrence, person) = templateWithVisibility()
        val day = LocalDate.of(2026, 10, 6)
        // Each runner gets its own connection, as two real runs would.
        val dslA = DSL.using(url, user, password)
        val dslB = DSL.using(url, user, password)
        val runnerA = RecurrenceRepository(dslA)
        val runnerB = RecurrenceRepository(dslB)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                listOf(runnerA, runnerB).map { repo ->
                    pool.submit<UUID?> {
                        start.await()
                        repo.createRound(recurrence, day, day.atTime(8, 0), day.atTime(17, 0), listOf(person))
                    }
                }
            start.countDown()
            val created = results.map { it.get(20, TimeUnit.SECONDS) }

            assertEquals(1, created.count { it != null })
            assertEquals(
                1,
                count("select count(*) from form.task where recurrence_id = '${recurrence.recurrenceId}' and period_date = '$day'"),
            )
        } finally {
            pool.shutdownNow()
            dslA.close()
            dslB.close()
        }
    }

    @Test
    fun `a round whose template has no form is rolled back completely`() {
        val creator = newUser("creator")
        val taskId = UUID.randomUUID()
        dsl.execute(
            "insert into form.task (task_id, title, open_at, close_at) values (?, ?, ?, ?)",
            taskId,
            "${IT_MARK}formless",
            opens,
            closes,
        )
        val recurrenceId = UUID.randomUUID()
        dsl.execute(
            "insert into form.task_recurrence (recurrence_id, template_task_id, frequency, start_date, created_by) values (?, ?, 'DAILY', '2026-10-05', ?)",
            recurrenceId,
            taskId,
            creator,
        )
        val recurrence = recurrences.fetchRunning(LocalDate.of(2026, 1, 1)).single { it.templateTaskId == taskId }

        assertThrows<IllegalStateException> {
            recurrences.createRound(
                recurrence,
                LocalDate.of(2026, 10, 6),
                LocalDateTime.of(2026, 10, 6, 8, 0),
                LocalDateTime.of(2026, 10, 6, 17, 0),
                emptyList(),
            )
        }

        assertEquals(0, count("select count(*) from form.task where recurrence_id = '$recurrenceId'"))
    }
}
