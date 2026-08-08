package com.cocoa.web

import com.cocoa.web.model.Analytics
import com.cocoa.web.model.Form
import com.cocoa.web.model.FormResponse
import com.cocoa.web.model.Question
import com.cocoa.web.model.Researcher
import com.cocoa.web.model.Section
import com.cocoa.web.model.Task
import com.cocoa.web.model.User
import com.cocoa.web.repository.FarmRepository
import com.cocoa.web.repository.FormRepository
import com.cocoa.web.repository.FormResponseRepository
import com.cocoa.web.repository.HarvestRepository
import com.cocoa.web.repository.LocationRepository
import com.cocoa.web.repository.QuestionRepository
import com.cocoa.web.repository.ResearcherRepository
import com.cocoa.web.repository.SectionRepository
import com.cocoa.web.repository.TaskRepository
import com.cocoa.web.repository.UserRepository
import com.cocoa.web.service.AuthenticationService
import com.cocoa.web.service.CookieService
import com.cocoa.web.service.FormResponseService
import com.cocoa.web.service.FormService
import com.cocoa.web.service.HarvestAnalyticsService
import com.cocoa.web.service.ResearcherService
import com.cocoa.web.service.SpatialHarvestAnalyticsService
import com.cocoa.web.service.TaskService
import com.cocoa.web.service.UserAnalyticsService
import com.cocoa.web.service.UserService
import com.cocoa.web.service.XlsxService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

/**
 * Full-context smoke test covering every controller endpoint, the security
 * filter chain, the global exception handler, and a handful of bean wirings.
 *
 * Strategy: a single @SpringBootTest with @AutoConfigureMockMvc. Production
 * repositories are replaced with @MockBean so no SQL actually runs — the H2
 * datasource from application-test.properties is only here to satisfy
 * jOOQ's auto-configuration at context startup.
 *
 * Service-layer beans that don't touch repositories (e.g. CookieService)
 * are mocked too because the security filter chain pulls them in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebApplicationTests {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    // ---- Repositories: never actually used at runtime (controllers/services
    // are mocked below), but Spring needs them to exist for the context to load.

    @MockBean lateinit var userRepository: UserRepository

    @MockBean lateinit var taskRepository: TaskRepository

    @MockBean lateinit var formRepository: FormRepository

    @MockBean lateinit var formResponseRepository: FormResponseRepository

    @MockBean lateinit var harvestRepository: HarvestRepository

    @MockBean lateinit var farmRepository: FarmRepository

    @MockBean lateinit var locationRepository: LocationRepository

    @MockBean lateinit var questionRepository: QuestionRepository

    @MockBean lateinit var researcherRepository: ResearcherRepository

    @MockBean lateinit var sectionRepository: SectionRepository

    // ---- Services (because AuthenticationController depends on most of these)

    @MockBean lateinit var authenticationService: AuthenticationService

    @MockBean lateinit var userService: UserService

    @MockBean lateinit var cookieService: CookieService

    @MockBean lateinit var formService: FormService

    @MockBean lateinit var formResponseService: FormResponseService

    @MockBean lateinit var taskService: TaskService

    @MockBean lateinit var researcherService: ResearcherService

    @MockBean lateinit var harvestAnalyticsService: HarvestAnalyticsService

    @MockBean lateinit var userAnalyticsService: UserAnalyticsService

    @MockBean lateinit var spatialHarvestAnalyticsService: SpatialHarvestAnalyticsService

    @MockBean lateinit var xlsxService: XlsxService

    // ----------------------------------------------------------------------
    // Context & security
    // ----------------------------------------------------------------------

    @Test
    fun contextLoads() {
        // If the Spring context fails to start this test fails. Successful run
        // means every @ConfigurationProperties, @Bean, @Component, and security
        // filter chain wires up cleanly.
    }

    @Test
    fun publicTestEndpointIsOpenWithoutAuth() {
        mockMvc.perform(get("/public/test"))
            .andExpect(status().isOk)
            .andExpect(content().string("This is Public route"))
    }

    @Test
    fun protectedTestEndpointReturns401WithoutAuth() {
        mockMvc.perform(get("/test"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun swaggerIsAccessibleWithoutAuth() {
        mockMvc.perform(get("/swagger-ui/index.html"))
            .andExpect { result -> assert(result.response.status != 401) { "Swagger UI must not require auth" } }
    }

    @Test
    fun apiDocsIsAccessibleWithoutAuth() {
        mockMvc.perform(get("/api-docs"))
            .andExpect { result -> assert(result.response.status != 401) { "API docs must not require auth" } }
    }

    // ----------------------------------------------------------------------
    // AuthenticationController
    // ----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "tester", authorities = ["read:profile:own"])
    fun authMe_returnsCurrentUser() {
        val userId = UUID.randomUUID()
        whenever(userService.getUserDetail(userId)).thenReturn(
            User.Detail(
                userId = userId,
                email = "tester@example.com",
                firstName = "Test",
                lastName = "User",
                organization = "Cocoa",
                isPasswordReset = false,
                isRequiresPasswordReset = false,
                roles = listOf("researcher"),
            ),
        )
        whenever(userService.getUserDetail(any<UUID>())).thenReturn(
            User.Detail(
                userId = userId,
                email = "tester@example.com",
                firstName = "Test",
                lastName = "User",
                organization = "Cocoa",
                isPasswordReset = false,
                isRequiresPasswordReset = false,
                roles = listOf("researcher"),
            ),
        )

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value.email").exists())
    }

    @Test
    @WithMockUser(authorities = ["read:profile:own", "update:profile:own"])
    fun authResetPassword_returns200() {
        val body =
            objectMapper.writeValueAsString(
                mapOf("newPassword" to "NewSecureP@ss1"),
            )

        mockMvc.perform(
            patch("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value").exists())
    }

    // ----------------------------------------------------------------------
    // TaskController
    // ----------------------------------------------------------------------

    @Test
    @WithMockUser
    fun tasks_getAll_returns200() {
        val now = LocalDateTime.now()
        whenever(taskService.getTasks()).thenReturn(
            listOf(
                Task.Entity(
                    taskId = UUID.randomUUID(),
                    title = "Sample",
                    description = "Sample task",
                    taskType = "FARMER",
                    openAt = now,
                    closeAt = now.plusDays(7),
                ),
            ),
        )

        mockMvc.perform(get("/tasks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value[0].title").value("Sample"))
    }

    @Test
    @WithMockUser
    fun tasks_getById_returns200() {
        val now = LocalDateTime.now()
        val taskId = UUID.randomUUID()
        whenever(taskService.getTask(taskId)).thenReturn(
            Task.Entity(
                taskId = taskId,
                title = "Sample",
                description = "Sample task",
                taskType = "FARMER",
                openAt = now,
                closeAt = now.plusDays(7),
            ),
        )

        mockMvc.perform(get("/tasks/{taskId}", taskId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value.taskId").exists())
    }

    // ----------------------------------------------------------------------
    // FormController
    // ----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = ["read:form:all"])
    fun forms_getAll_returns200() {
        whenever(formService.getForms()).thenReturn(
            listOf(
                Form.Entity(
                    formId = UUID.randomUUID(),
                    taskId = UUID.randomUUID(),
                    title = "Cocoa Form",
                    content = "{}",
                    handler = "default",
                    isMultipleSubmit = false,
                    description = "Test form",
                    createdAt = LocalDateTime.now(),
                ),
            ),
        )

        mockMvc.perform(get("/forms"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value[0].title").value("Cocoa Form"))
    }

    @Test
    @WithMockUser(authorities = ["read:form:all"])
    fun forms_getById_returns200() {
        val formId = UUID.randomUUID()
        whenever(formService.getFormDetail(formId)).thenReturn(
            Form.Detail(
                formId = formId,
                title = "Form Title",
                description = "Description",
                sections = emptyList(),
            ),
        )

        mockMvc.perform(get("/forms/{formId}", formId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value.title").value("Form Title"))
    }

    @Test
    @WithMockUser(authorities = ["update:form:all"])
    fun forms_edit_returns200() {
        val formId = UUID.randomUUID()
        val sectionId = UUID.randomUUID()
        val questionId = UUID.randomUUID()
        val body =
            objectMapper.writeValueAsString(
                Form.Request.Edit(
                    description = "Updated desc",
                    sections =
                        listOf(
                            Section.Request.Edit(
                                sectionId = sectionId,
                                description = "Updated section",
                                isActive = true,
                                questions =
                                    listOf(
                                        Question.Request.Edit(
                                            questionId = questionId,
                                            description = "Updated question",
                                            isActive = true,
                                            isMandatory = false,
                                        ),
                                    ),
                            ),
                        ),
                ),
            )

        mockMvc.perform(
            put("/forms/{formId}/edit", formId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
    }

    // ----------------------------------------------------------------------
    // FormResponseController
    // ----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = ["read:form:all"])
    fun formResponses_getAll_returns200() {
        val taskId = UUID.randomUUID()
        whenever(formResponseService.getUserResponse(taskId)).thenReturn(
            listOf(
                FormResponse.Detail(
                    questionTitle = "Sample question",
                    answers = emptyList(),
                ),
            ),
        )

        mockMvc.perform(get("/tasks/{taskId}/responses", taskId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value[0].questionTitle").value("Sample question"))
    }

    @Test
    @WithMockUser(authorities = ["read:form:all"])
    fun formResponses_getById_returns200() {
        val taskId = UUID.randomUUID()
        val responseId = UUID.randomUUID()
        whenever(formResponseService.getFormResponse(taskId, responseId)).thenReturn(
            FormResponse.Entity(
                responseId = responseId,
                formId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                submittedAt = LocalDateTime.now(),
                answer = objectMapper.createObjectNode(),
                status = "SUBMITTED",
            ),
        )

        mockMvc.perform(get("/tasks/{taskId}/responses/{responseId}", taskId, responseId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value.responseId").exists())
    }

    // ----------------------------------------------------------------------
    // AnalyticsController
    // ----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = ["read:report:all"])
    fun analytics_harvestSeriesAndSummary_allReturn200() {
        val emptyReport =
            Analytics.Report.TimeSeries(
                from = YearMonth.of(2026, 1),
                to = YearMonth.of(2026, 6),
                title = "t",
                metadata = emptyMap(),
                series = emptyList(),
            )
        val emptySummary =
            Analytics.Report.Summary(
                from = YearMonth.of(2026, 1),
                to = YearMonth.of(2026, 6),
                title = "t",
                metadata = emptyMap(),
                data = Analytics.Report.DataItem(label = "Total", value = 0.0, unit = "kg"),
            )

        whenever(harvestAnalyticsService.getCumulativeSeries(any())).thenReturn(emptyReport)
        whenever(harvestAnalyticsService.getDeltaSeries(any())).thenReturn(emptyReport)
        whenever(harvestAnalyticsService.getAverageSeries(any())).thenReturn(emptyReport)
        whenever(harvestAnalyticsService.getFrequencySeries(any())).thenReturn(emptyReport)
        whenever(harvestAnalyticsService.getTotalSummary(any())).thenReturn(emptySummary)
        whenever(harvestAnalyticsService.getFrequencySummary(any())).thenReturn(emptySummary)

        val base = "/analytics/harvest"
        val params = arrayOf("from=2026-01", "to=2026-06")

        mockMvc.perform(get("$base/time-series/sum").param("from", "2026-01").param("to", "2026-06"))
            .andExpect(status().isOk)
        mockMvc.perform(get("$base/time-series/delta").param("from", "2026-01").param("to", "2026-06"))
            .andExpect(status().isOk)
        mockMvc.perform(get("$base/time-series/average").param("from", "2026-01").param("to", "2026-06"))
            .andExpect(status().isOk)
        mockMvc.perform(get("$base/time-series/frequency").param("from", "2026-01").param("to", "2026-06"))
            .andExpect(status().isOk)
        mockMvc.perform(get("$base/summary/total").param("from", "2026-01").param("to", "2026-06"))
            .andExpect(status().isOk)
        mockMvc.perform(get("$base/summary/count").param("from", "2026-01").param("to", "2026-06"))
            .andExpect(status().isOk)

        // silence the unused-variable warnings
        assert(params.isNotEmpty())
    }

    @Test
    @WithMockUser(authorities = ["read:report:all"])
    fun analytics_userSeriesAndSummary_allReturn200() {
        val report =
            Analytics.Report.TimeSeries(
                from = YearMonth.of(2026, 1),
                to = YearMonth.of(2026, 6),
                title = "t",
                metadata = emptyMap(),
                series = emptyList(),
            )
        val summary =
            Analytics.Report.Summary(
                from = YearMonth.of(2026, 1),
                to = YearMonth.of(2026, 6),
                title = "t",
                metadata = emptyMap(),
                data = Analytics.Report.DataItem(label = "Total", value = 0L, unit = "users"),
            )

        whenever(userAnalyticsService.getCumulativeSeries(any())).thenReturn(report)
        whenever(userAnalyticsService.getDeltaSeries(any())).thenReturn(report)
        whenever(userAnalyticsService.getCountSummary(any())).thenReturn(summary)

        val base = "/analytics/users"
        mockMvc.perform(get("$base/time-series/sum").param("from", "2026-01").param("to", "2026-06"))
            .andExpect(status().isOk)
        mockMvc.perform(get("$base/time-series/delta").param("from", "2026-01").param("to", "2026-06"))
            .andExpect(status().isOk)
        mockMvc.perform(get("$base/summary/count").param("from", "2026-01").param("to", "2026-06"))
            .andExpect(status().isOk)
    }

    // ----------------------------------------------------------------------
    // SpatialAnalyticsController
    // ----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = ["read:report:all"])
    fun spatialAnalytics_allEndpoints_return200() {
        val report =
            Analytics.Report.TimeSeries(
                from = YearMonth.of(2026, 1),
                to = YearMonth.of(2026, 6),
                title = "t",
                metadata = mapOf("type" to "spatial_selection"),
                series = emptyList(),
            )
        val summary =
            Analytics.Report.Summary(
                from = YearMonth.of(2026, 1),
                to = YearMonth.of(2026, 6),
                title = "t",
                metadata = mapOf("type" to "spatial_selection"),
                data = Analytics.Report.DataItem(label = "Total", value = 0.0, unit = "kg"),
            )

        whenever(spatialHarvestAnalyticsService.getSpatialDeltaSeries(any())).thenReturn(report)
        whenever(spatialHarvestAnalyticsService.getSpatialCumulativeSeries(any())).thenReturn(report)
        whenever(spatialHarvestAnalyticsService.getSpatialAverageSeries(any())).thenReturn(report)
        whenever(spatialHarvestAnalyticsService.getSpatialFrequencySeries(any())).thenReturn(report)
        whenever(spatialHarvestAnalyticsService.getSpatialTotalSummary(any())).thenReturn(summary)
        whenever(spatialHarvestAnalyticsService.getSpatialFrequencySummary(any())).thenReturn(summary)

        val base = "/analytics/harvest/spatial"
        mockMvc.perform(
            post("$base/time-series/delta")
                .param("from", "2026-01")
                .param("to", "2026-06")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geoJson\":\"{}\"}"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("$base/time-series/sum")
                .param("from", "2026-01")
                .param("to", "2026-06")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geoJson\":\"{}\"}"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("$base/time-series/average")
                .param("from", "2026-01")
                .param("to", "2026-06")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geoJson\":\"{}\"}"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("$base/time-series/frequency")
                .param("from", "2026-01")
                .param("to", "2026-06")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geoJson\":\"{}\"}"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("$base/summary/total")
                .param("from", "2026-01")
                .param("to", "2026-06")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geoJson\":\"{}\"}"),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("$base/summary/count")
                .param("from", "2026-01")
                .param("to", "2026-06")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geoJson\":\"{}\"}"),
        ).andExpect(status().isOk)
    }

    @Test
    @WithMockUser(authorities = ["read:report:all"])
    fun spatialAnalytics_invalidDateRange_returns400() {
        // from > to should throw IllegalArgumentException -> 400 via GlobalExceptionHandler
        mockMvc.perform(
            post("/analytics/harvest/spatial/time-series/delta")
                .param("from", "2026-06")
                .param("to", "2026-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"geoJson\":\"{}\"}"),
        ).andExpect(status().isBadRequest)
    }

    // ----------------------------------------------------------------------
    // ReportController
    // ----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = ["read:report:all"])
    fun report_downloadRawData_returnsXlsx() {
        whenever(xlsxService.farmSheetBuilder()).thenReturn({})
        whenever(xlsxService.harvestSheetBuilder()).thenReturn({})
        whenever(xlsxService.buildXlsxFile(any(), any())).thenReturn(ByteArray(0))

        mockMvc.perform(get("/reports/raw-data/download"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
    }

    // ----------------------------------------------------------------------
    // ResearcherController
    // ----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = ["read:researcher:all"])
    fun researchers_list_returns200() {
        val userId = UUID.randomUUID()
        whenever(researcherService.getResearchers(any())).thenReturn(
            listOf(
                Researcher.Entity(
                    userId = userId,
                    firstName = "Alice",
                    lastName = "Brown",
                    organization = "Cocoa Inst",
                ),
            ),
        )

        mockMvc.perform(get("/researchers"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value[0].firstName").value("Alice"))
    }

    @Test
    @WithMockUser(username = "me", authorities = ["read:researcher:own"])
    fun researchers_getMe_returns200() {
        val userId = UUID.randomUUID()
        whenever(userService.getUserDetail(any<UUID>())).thenReturn(
            User.Detail(
                userId = userId,
                email = "me@example.com",
                firstName = null,
                lastName = null,
                organization = null,
                isPasswordReset = false,
                isRequiresPasswordReset = false,
                roles = listOf("researcher"),
            ),
        )
        whenever(researcherService.getResearcher(any<UUID>())).thenReturn(
            Researcher.Entity(
                userId = userId,
                firstName = "Me",
                lastName = "Myself",
                organization = "Self",
            ),
        )

        mockMvc.perform(get("/researchers/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value.firstName").value("Me"))
    }

    @Test
    @WithMockUser(authorities = ["read:researcher:all"])
    fun researchers_getById_returns200() {
        val userId = UUID.randomUUID()
        whenever(researcherService.getResearcher(userId)).thenReturn(
            Researcher.Entity(
                userId = userId,
                firstName = "Bob",
                lastName = "Smith",
                organization = "ACME",
            ),
        )

        mockMvc.perform(get("/researchers/{userId}", userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.value.firstName").value("Bob"))
    }

    // ----------------------------------------------------------------------
    // AdminController
    // ----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = ["create:user:any"])
    fun admin_createUser_returns201() {
        val body =
            objectMapper.writeValueAsString(
                User.Request.Register(
                    username = "newuser@example.com",
                    password = "TempP@ss123",
                ),
            )

        mockMvc.perform(
            post("/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isCreated)
    }

    // ----------------------------------------------------------------------
    // GlobalExceptionHandler
    // ----------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = ["read:profile:own"])
    fun authMe_userNotFound_returns404() {
        whenever(userService.getUserDetail(any<UUID>())).thenAnswer {
            throw com.cocoa.web.exception.EntityNotFoundException("User not found")
        }

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").exists())
    }

    // ----------------------------------------------------------------------
    // Helper: avoid "unused import" warnings for JsonNode which is needed by
    // other tests in the suite but not this one.
    // ----------------------------------------------------------------------

    @Suppress("unused")
    private fun touchJsonNode(node: JsonNode) = node.toString()
}
