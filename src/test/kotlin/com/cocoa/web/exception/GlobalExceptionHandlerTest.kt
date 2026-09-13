package com.cocoa.web.exception

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.cocoa.web.WebApplicationTests
import com.cocoa.web.security.WithMockPrincipal
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Verifies the status-code mapping in [GlobalExceptionHandler] by re-using the
 * @SpringBootTest context from [WebApplicationTests] (inherited). The parent's
 * @MockBean for UserService is re-stubbed here to throw each exception type
 * and we assert the resulting HTTP status.
 */
class GlobalExceptionHandlerTest : WebApplicationTests() {
    @Test
    @WithMockPrincipal(authorities = ["read:profile:own"])
    fun entityNotFound_mapsTo404() {
        whenever(userService.getUserDetail(any<UUID>())).thenAnswer {
            throw EntityNotFoundException("missing user")
        }

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("missing user"))
    }

    @Test
    @WithMockPrincipal(authorities = ["read:profile:own"])
    fun illegalArgument_mapsTo400() {
        whenever(userService.getUserDetail(any<UUID>())).thenAnswer {
            throw IllegalArgumentException("bad input")
        }

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("bad input"))
    }

    @Test
    @WithMockPrincipal(authorities = ["read:profile:own"])
    fun permissionDenied_mapsTo403() {
        whenever(userService.getUserDetail(any<UUID>())).thenAnswer {
            throw PermissionDeniedException("nope")
        }

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error").value("nope"))
    }

    @Test
    @WithMockPrincipal(authorities = ["read:profile:own"])
    fun genericException_mapsTo500() {
        // BE-7: the raw exception message ("boom") must never reach the
        // client -- it could be a JDBC/jOOQ failure carrying SQL/schema
        // details. Client sees a generic message; the real one is logged
        // server-side instead (see genericException_isLogged below).
        whenever(userService.getUserDetail(any<UUID>())).thenAnswer {
            throw RuntimeException("boom")
        }

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("Internal server error"))
    }

    @Test
    @WithMockPrincipal(authorities = ["read:profile:own"])
    fun genericException_isLogged() {
        val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)

        try {
            whenever(userService.getUserDetail(any<UUID>())).thenAnswer {
                throw RuntimeException("boom")
            }

            mockMvc.perform(get("/auth/me"))
                .andExpect(status().isInternalServerError)

            val errorEvents = appender.list.filter { it.level == Level.ERROR }
            assert(errorEvents.isNotEmpty()) { "Expected the unhandled exception to be logged at ERROR" }
            assert(errorEvents.any { it.throwableProxy?.message == "boom" }) {
                "Expected the logged event to carry the original exception, not just a generic message"
            }
        } finally {
            logger.detachAppender(appender)
        }
    }

    @Test
    @WithMockUser
    fun typeMismatch_mapsTo400() {
        // TaskController accepts UUID for /tasks/{taskId}; a non-UUID triggers
        // MethodArgumentTypeMismatchException -> 400 via the global handler.
        mockMvc.perform(get("/tasks/{bad}", "not-a-uuid"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }
}
